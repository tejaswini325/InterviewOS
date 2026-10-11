package com.interviewos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider;
import com.interviewos.ai.AiProvider.PersonalizedQuestion;
import com.interviewos.adaptive.AdaptiveEngine;
import com.interviewos.adaptive.AdaptiveEngine.*;
import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.*;
import com.interviewos.exception.ApiException;
import com.interviewos.jobs.JobService;
import com.interviewos.repository.*;
import com.interviewos.service.QuestionMetaCache.QuestionMeta;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {
    static final Duration GRACE = Duration.ofSeconds(30);   // network latency allowance for the final answer

    private final SessionRepository sessions;
    private final InterviewQuestionRepository iqs;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final EvaluationRepository evals;
    private final ReportRepository reports;
    private final QuestionCatalog catalog;
    private final AdaptiveEngine engine;
    private final EvaluationService evaluation;
    private final JobService jobs;
    private final ObjectMapper om;
    private final Clock clock;
    private final BookmarkRepository bookmarks;
    private final TurnRepository turns;
    private final AiProvider ai;
    private final ResumeService resumes;
    private final ResumeQuestionGenerator resumeQuestions;

    public InterviewService(SessionRepository sessions, InterviewQuestionRepository iqs, QuestionRepository questions,
                            AnswerRepository answers, EvaluationRepository evals, ReportRepository reports,
                            QuestionCatalog catalog, AdaptiveEngine engine, EvaluationService evaluation, JobService jobs,
                            ObjectMapper om, Clock clock, BookmarkRepository bookmarks, TurnRepository turns, AiProvider ai,
                            ResumeService resumes, ResumeQuestionGenerator resumeQuestions) {
        this.bookmarks = bookmarks; this.turns = turns; this.ai = ai;
        this.resumes = resumes; this.resumeQuestions = resumeQuestions;
        this.sessions = sessions; this.iqs = iqs; this.questions = questions; this.answers = answers; this.evals = evals;
        this.reports = reports; this.catalog = catalog; this.engine = engine; this.evaluation = evaluation; this.jobs = jobs;
        this.om = om; this.clock = clock;
    }

    // ------------------------------------------------------------------ start
    @Transactional
    public SessionView start(Long userId, StartRequest r) {
        Optional<InterviewSession> active = sessions.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, SessionStatus.IN_PROGRESS);
        if (active.isPresent()) {
            InterviewSession a = sessions.lockById(active.get().getId()).orElseThrow();
            if (a.getStatus() == SessionStatus.IN_PROGRESS) {
                if (clock.instant().isAfter(a.getDeadlineAt())) completeInternal(a);
                else throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_SESSION_EXISTS",
                        "You already have an interview in progress. Resume or finish it first.", Map.of("sessionId", a.getId()));
            }
        }
        InterviewMode mode = r.mode() == null ? InterviewMode.TECHNICAL : r.mode();
        List<Topic> topics = r.topics().stream().distinct().toList();
        if (!mode.topics().containsAll(topics)) {
            throw ApiException.badRequest("INVALID_TOPICS_FOR_MODE", "Topics " + topics + " cannot be used in " + mode + " mode; allowed: " + mode.topics());
        }
        String resumeText = null;
        if (mode == InterviewMode.RESUME) {
            if (topics.stream().noneMatch(topic -> topic != Topic.PROJECT)) {
                throw ApiException.badRequest("TECHNICAL_TOPIC_REQUIRED", "Select at least one technical topic for a resume interview.");
            }
            resumeText = resumes.textForInterview(userId).orElseThrow(
                    () -> ApiException.badRequest("RESUME_REQUIRED", "Upload a PDF or DOCX resume before starting this interview."));
        } else {
            long available = catalog.eligible().stream().filter(q -> topics.contains(q.topic())).count();
            if (available < r.questionCount()) {
                throw ApiException.badRequest("NOT_ENOUGH_QUESTIONS", "Only " + available + " question(s) are available for the selected topics; choose fewer questions or more topics.");
            }
        }
        Instant now = clock.instant();
        InterviewSession s = new InterviewSession();
        s.setUserId(userId); s.setTargetRole(r.targetRole()); s.setMode(mode);
        s.setTopics(topics.stream().map(Enum::name).collect(Collectors.joining(",")));
        s.setBaseDifficulty(r.difficulty()); s.setCurrentDifficulty(r.difficulty());
        s.setDurationMinutes(r.durationMinutes()); s.setQuestionCount(r.questionCount());
        s.setStatus(SessionStatus.IN_PROGRESS); s.setStartedAt(now); s.setDeadlineAt(now.plus(Duration.ofMinutes(r.durationMinutes())));
        sessions.save(s);
        if (mode == InterviewMode.RESUME) {
            List<PersonalizedQuestion> customQuestions = resumeQuestions.generate(resumeText, r.targetRole(), topics, r.difficulty(), r.questionCount());
            savePersonalizedQuestions(s, customQuestions);
        }
        selectNext(s);
        return view(s);
    }

    // ------------------------------------------------------------------ read
    @Transactional(readOnly = true)
    public SessionView get(Long userId, Long id) { return view(owned(userId, id)); }

    @Transactional(readOnly = true)
    public PageResponse<SessionSummary> list(Long userId, SessionStatus status, TargetRole role, InterviewMode mode, Topic topic, LocalDate from, LocalDate to, int page, int size) {
        Specification<InterviewSession> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (role != null) p.add(cb.equal(root.get("targetRole"), role));
            if (mode != null) p.add(cb.equal(root.get("mode"), mode));
            if (topic != null) p.add(cb.like(cb.concat(",", cb.concat(root.<String>get("topics"), ",")), "%," + topic.name() + ",%"));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from.atStartOfDay(ZoneOffset.UTC).toInstant()));
            if (to != null) p.add(cb.lessThan(root.get("startedAt"), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<InterviewSession> res = sessions.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50), Sort.by(Sort.Direction.DESC, "startedAt")));
        Map<Long, Double> scores = reports.findBySessionIdIn(res.getContent().stream().map(InterviewSession::getId).toList())
                .stream().collect(Collectors.toMap(InterviewReport::getSessionId, InterviewReport::getOverallScore));
        List<SessionSummary> content = res.getContent().stream().map(s -> new SessionSummary(s.getId(), s.getStatus(), s.getTargetRole(), s.getMode(),
                topicsOf(s), s.getBaseDifficulty(), s.getQuestionCount(), (int) answers.countBySession(s.getId()),
                s.getStartedAt(), s.getCompletedAt(), scores.get(s.getId()))).toList();
        return new PageResponse<>(content, res.getNumber(), res.getSize(), res.getTotalElements(), res.getTotalPages());
    }

    // ------------------------------------------------------------------ next question
    @Transactional
    public NextQuestionResponse nextQuestion(Long userId, Long id) {
        InterviewSession s = sessions.lockByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        int answered = (int) answers.countBySession(s.getId());
        if (s.getStatus() != SessionStatus.IN_PROGRESS) return new NextQuestionResponse(true, "COMPLETED", null, answered, s.getQuestionCount());
        if (clock.instant().isAfter(s.getDeadlineAt())) return new NextQuestionResponse(true, "TIME_UP", null, answered, s.getQuestionCount());

        Optional<QuestionView> pending = currentUnanswered(s);
        if (pending.isPresent()) return new NextQuestionResponse(false, null, pending.get(), answered, s.getQuestionCount());   // resume / double-click safe
        if (answered >= s.getQuestionCount()) return new NextQuestionResponse(true, "ALL_ANSWERED", null, answered, s.getQuestionCount());

        Optional<InterviewQuestion> next = selectNext(s);
        if (next.isEmpty()) return new NextQuestionResponse(true, "QUESTION_POOL_EXHAUSTED", null, answered, s.getQuestionCount());
        return new NextQuestionResponse(false, null, toView(next.get(), s.getUserId()), answered, s.getQuestionCount());
    }

    // ------------------------------------------------------------------ submit answer
    @Transactional
    public AnswerAck submitAnswer(Long userId, Long id, AnswerRequest r) {
        InterviewSession s = sessions.lockByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        InterviewQuestion iq = iqs.findByIdAndSessionId(r.interviewQuestionId(), id).orElseThrow(() -> ApiException.notFound("Question"));

        Optional<CandidateAnswer> existing = answers.findByInterviewQuestionId(iq.getId());
        if (existing.isPresent()) {   // duplicate submission: return the original result, never re-evaluate or re-adapt
            return new AnswerAck(existing.get().getId(), true, (int) answers.countBySession(id), s.getQuestionCount(), null);
        }
        if (s.getStatus() != SessionStatus.IN_PROGRESS) throw ApiException.conflict("NOT_IN_PROGRESS", "This interview is no longer in progress");
        if (clock.instant().isAfter(s.getDeadlineAt().plus(GRACE))) throw ApiException.conflict("TIME_EXPIRED", "The interview time has expired");

        Question q = questions.findById(iq.getQuestionId()).orElseThrow(() -> ApiException.notFound("Question"));
        validateAnswer(q, r);

        CandidateAnswer a = new CandidateAnswer();
        a.setInterviewQuestionId(iq.getId()); a.setSkipped(r.skipped());
        a.setAnswerText(r.skipped() ? null : r.answerText()); a.setSelectedOption(r.skipped() ? null : r.selectedOption());
        a.setTimeSpentSec(r.timeSpentSec()); a.setSubmittedAt(clock.instant());
        answers.save(a);

        AnswerEvaluation e = evaluation.evaluateProvisional(q, a);
        if (e.getScore() != null) {
            Adjustment adj = engine.applyResult(new State(s.getCurrentDifficulty(), s.getStrongStreak(), s.getWeakStreak()), e.getScore());
            s.setCurrentDifficulty(adj.state().current()); s.setStrongStreak(adj.state().strongStreak()); s.setWeakStreak(adj.state().weakStreak());
            s.setLastAdjustment(adj.reason());
        }
        if (!e.isFinalized()) jobs.enqueue(JobKind.ANSWER, a.getId(), s.getId(), "answer:" + a.getId());
        return new AnswerAck(a.getId(), false, (int) answers.countBySession(id), s.getQuestionCount(), null);
    }

    // ------------------------------------------------------------------ follow-up reply
    /** The candidate's reply (or skip) to the interviewer's follow-up. Idempotent. Does not re-adapt difficulty. */
    @Transactional
    public TurnView submitFollowUp(Long userId, Long id, FollowUpAnswerRequest r) {
        InterviewSession s = sessions.lockByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        InterviewQuestion iq = iqs.findByIdAndSessionId(r.interviewQuestionId(), id).orElseThrow(() -> ApiException.notFound("Question"));
        turns.findByInterviewQuestionIdAndKind(iq.getId(), TurnKind.FOLLOW_UP).orElseThrow(() -> ApiException.notFound("Follow-up"));
        Optional<InterviewTurn> existing = turns.findByInterviewQuestionIdAndKind(iq.getId(), TurnKind.FOLLOW_UP_ANSWER);
        if (existing.isPresent()) return turnView(existing.get());
        if (s.getStatus() != SessionStatus.IN_PROGRESS) throw ApiException.conflict("NOT_IN_PROGRESS", "This interview is no longer in progress");

        boolean skip = r.skipped() || r.answerText() == null || r.answerText().isBlank();
        InterviewTurn t = new InterviewTurn();
        t.setSessionId(id); t.setInterviewQuestionId(iq.getId()); t.setKind(TurnKind.FOLLOW_UP_ANSWER); t.setSource(TurnSource.CANDIDATE);
        t.setContent(skip ? EvaluationService.SKIPPED_MARK : r.answerText().trim()); t.setCreatedAt(clock.instant());
        turns.save(t);
        if (!skip) {
            CandidateAnswer a = answers.findByInterviewQuestionId(iq.getId()).orElseThrow(() -> ApiException.notFound("Answer"));
            Question q = questions.findById(iq.getQuestionId()).orElseThrow();
            evaluation.rescoreAfterFollowUp(q, a);
        }
        return turnView(t);
    }

    private TurnView pendingFollowUp(InterviewSession s) {
        List<InterviewQuestion> served = iqs.findBySessionIdOrderByPosition(s.getId());
        if (served.isEmpty()) return null;
        InterviewQuestion last = served.get(served.size() - 1);
        if (answers.findByInterviewQuestionId(last.getId()).isEmpty()) return null;
        if (turns.findByInterviewQuestionIdAndKind(last.getId(), TurnKind.FOLLOW_UP_ANSWER).isPresent()) return null;
        return turns.findByInterviewQuestionIdAndKind(last.getId(), TurnKind.FOLLOW_UP).map(InterviewService::turnView).orElse(null);
    }

    static TurnView turnView(InterviewTurn t) {
        return new TurnView(t.getId(), t.getInterviewQuestionId(), t.getKind().name(), t.getSource().name(), t.getContent());
    }

    // ------------------------------------------------------------------ complete
    @Transactional
    public SessionView complete(Long userId, Long id) {
        InterviewSession s = sessions.lockByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        if (s.getStatus() == SessionStatus.IN_PROGRESS) completeInternal(s);   // idempotent for COMPLETED / FAILED
        return view(s);
    }

    /** Called by the sweeper for interviews abandoned past their deadline. */
    @Transactional
    public void completeExpired(Long id) {
        sessions.lockById(id).filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).ifPresent(this::completeInternal);
    }

    private void completeInternal(InterviewSession s) {
        s.setStatus(SessionStatus.COMPLETED);
        s.setCompletedAt(clock.instant());
        jobs.enqueue(JobKind.REPORT, s.getId(), s.getId(), "report:" + s.getId());
    }

    // ------------------------------------------------------------------ internals
    private void savePersonalizedQuestions(InterviewSession session, List<PersonalizedQuestion> generated) {
        if (generated.size() != session.getQuestionCount()) {
            throw new IllegalStateException("Personalized question generator returned an unexpected question count");
        }
        for (int i = 0; i < generated.size(); i++) {
            PersonalizedQuestion draft = generated.get(i);
            Topic topic = Topic.valueOf(draft.topic());
            if (!session.topicList().contains(topic.name())) throw new IllegalStateException("Personalized question uses an unselected topic");

            Question question = new Question();
            question.setCode("resume-" + session.getId() + "-" + (i + 1));
            question.setTopic(topic);
            question.setSubtopic((draft.kind().equals("RESUME") ? "Resume: " : "Role: ") + topic.name());
            question.setDifficulty(session.getBaseDifficulty());
            question.setType(QuestionType.LONG);
            question.setStatement(draft.question());
            question.setRubric(resumeQuestions.rubricJson(draft.concepts()));
            question.setTimeEstimateSec(120);
            question.setVersion(1);
            question.setActive(false);
            question.setPersonalizedSessionId(session.getId());
            question.setPersonalizedPosition(i + 1);
            question.getTags().add("PERSONALIZED");
            question.getTags().add(draft.kind());
            questions.save(question);
        }
    }

    private Optional<InterviewQuestion> selectNext(InterviewSession s) {
        List<InterviewQuestion> served = iqs.findBySessionIdOrderByPosition(s.getId());
        Set<Long> used = served.stream().map(InterviewQuestion::getQuestionId).collect(Collectors.toSet());   // no repeats within an interview
        if (s.getMode() == InterviewMode.RESUME) {
            Question pick = questions.findByPersonalizedSessionIdOrderByPersonalizedPositionAsc(s.getId()).stream()
                    .filter(question -> !used.contains(question.getId())).findFirst().orElse(null);
            if (pick == null) return Optional.empty();
            InterviewQuestion iq = new InterviewQuestion();
            iq.setSessionId(s.getId()); iq.setQuestionId(pick.getId()); iq.setPosition(served.size() + 1);
            iq.setDifficultyAtSelection(s.getCurrentDifficulty());
            iq.setSelectionReason("Personalized " + pick.getSubtopic() + " question for " + s.getTargetRole() + ".");
            iq.setPresentedAt(clock.instant());
            return Optional.of(iqs.save(iq));
        }
        List<Topic> topics = s.topicList().stream().map(Topic::valueOf).toList();
        List<QuestionMeta> eligible = catalog.eligible();
        List<QuestionMeta> pool = eligible.stream().filter(q -> topics.contains(q.topic()) && !used.contains(q.id())).toList();

        Map<String, Map<Difficulty, Integer>> available = new HashMap<>();
        for (QuestionMeta q : pool) available.computeIfAbsent(q.topic().name(), k -> new EnumMap<>(Difficulty.class)).merge(q.difficulty(), 1, Integer::sum);

        Map<Long, QuestionMeta> metaById = eligible.stream().collect(Collectors.toMap(QuestionMeta::id, q -> q));
        Map<String, int[]> asked = new HashMap<>();       // [asked, scored]
        Map<String, Double> sums = new HashMap<>();
        for (InterviewQuestion iq : served) asked.computeIfAbsent(metaById.get(iq.getQuestionId()).topic().name(), k -> new int[2])[0]++;
        for (var row : evals.scoresBySession(s.getId())) {
            if (row.getScore() == null) continue;
            String t = metaById.get(row.getQuestionId()).topic().name();
            asked.computeIfAbsent(t, k -> new int[2])[1]++;
            sums.merge(t, row.getScore(), Double::sum);
        }
        Map<String, TopicStats> stats = new HashMap<>();
        asked.forEach((t, c) -> stats.put(t, new TopicStats(c[0], c[1], c[1] == 0 ? 0.5 : sums.getOrDefault(t, 0.0) / c[1])));

        Optional<Decision> d = engine.choose(new State(s.getCurrentDifficulty(), s.getStrongStreak(), s.getWeakStreak()),
                topics.stream().map(Enum::name).toList(), stats, available);
        if (d.isEmpty()) return Optional.empty();

        List<QuestionMeta> candidates = pool.stream().filter(q -> q.topic().name().equals(d.get().topic()) && q.difficulty() == d.get().difficulty())
                .sorted(Comparator.comparing(QuestionMeta::id)).toList();
        int position = served.size() + 1;
        // deterministic pick: reproducible for a given session and position, so adaptation can be explained and replayed
        QuestionMeta pick = candidates.get(Math.floorMod(Objects.hash(s.getId(), position), candidates.size()));

        InterviewQuestion iq = new InterviewQuestion();
        iq.setSessionId(s.getId()); iq.setQuestionId(pick.id()); iq.setPosition(position);
        iq.setDifficultyAtSelection(pick.difficulty());
        String prefix = s.getLastAdjustment() != null ? s.getLastAdjustment() + " " : "Baseline difficulty " + s.getBaseDifficulty() + ". ";
        String reason = prefix + d.get().reason();
        iq.setSelectionReason(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        iq.setPresentedAt(clock.instant());
        return Optional.of(iqs.save(iq));
    }

    private Optional<QuestionView> currentUnanswered(InterviewSession s) {
        List<InterviewQuestion> served = iqs.findBySessionIdOrderByPosition(s.getId());
        for (int i = served.size() - 1; i >= 0; i--) {
            if (answers.findByInterviewQuestionId(served.get(i).getId()).isEmpty()) return Optional.of(toView(served.get(i), s.getUserId()));
        }
        return Optional.empty();
    }

    private void validateAnswer(Question q, AnswerRequest r) {
        if (r.skipped()) return;
        if (q.getType() == QuestionType.MCQ) {
            if (r.selectedOption() == null || r.selectedOption().isBlank()) throw ApiException.badRequest("ANSWER_REQUIRED", "Select an option or skip the question");
            try {
                boolean valid = false;
                for (var n : om.readTree(q.getOptions())) valid |= n.path("id").asText().equalsIgnoreCase(r.selectedOption().trim());
                if (!valid) throw ApiException.badRequest("INVALID_OPTION", "Unknown option");
            } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        } else if (r.answerText() == null || r.answerText().isBlank()) {
            throw ApiException.badRequest("ANSWER_REQUIRED", "Write an answer or skip the question");
        }
    }

    private InterviewSession owned(Long userId, Long id) {
        // Ownership is part of the query: another candidate's id is indistinguishable from a missing one (404, not 403).
        return sessions.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Interview"));
    }

    private SessionView view(InterviewSession s) {
        long remaining = s.getStatus() == SessionStatus.IN_PROGRESS ? Math.max(0, Duration.between(clock.instant(), s.getDeadlineAt()).getSeconds()) : 0;
        QuestionView current = s.getStatus() == SessionStatus.IN_PROGRESS ? currentUnanswered(s).orElse(null) : null;
        TurnView pending = s.getStatus() == SessionStatus.IN_PROGRESS && current == null ? pendingFollowUp(s) : null;
        return new SessionView(s.getId(), s.getStatus(), s.getTargetRole(), s.getMode(), topicsOf(s), s.getBaseDifficulty(), s.getCurrentDifficulty(),
                s.getDurationMinutes(), s.getQuestionCount(), (int) answers.countBySession(s.getId()), s.getStartedAt(), s.getDeadlineAt(),
                s.getCompletedAt(), remaining, current, pending, ai.enabled());
    }

    private List<Topic> topicsOf(InterviewSession s) { return s.topicList().stream().map(Topic::valueOf).toList(); }

    private QuestionView toView(InterviewQuestion iq, Long userId) {
        Question q = questions.findById(iq.getQuestionId()).orElseThrow();
        try {
            List<OptionView> options = q.getOptions() == null ? null : om.readValue(q.getOptions(), new TypeReference<List<OptionView>>() {});
            List<TestCaseView> tests = q.getType() == QuestionType.CODING && q.getTestCases() != null
                    ? om.readValue(q.getTestCases(), new TypeReference<List<TestCaseView>>() {}) : null;
            return new QuestionView(iq.getId(), q.getId(), bookmarks.findByUserIdAndQuestionId(userId, q.getId()).isPresent(), iq.getPosition(), q.getTopic(), q.getSubtopic(), iq.getDifficultyAtSelection(), q.getType(),
                    q.getStatement(), options, tests, new ArrayList<>(q.getTags()), q.getTimeEstimateSec(), iq.getPresentedAt());
        } catch (JsonProcessingException e) { throw new IllegalStateException("Corrupt question JSON for " + q.getCode(), e); }
    }
}
