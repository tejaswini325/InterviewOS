package com.interviewos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.domain.*;
import com.interviewos.dto.ReportDtos.*;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.*;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    static final double EMA_ALPHA = 0.4;

    private final SessionRepository sessions;
    private final InterviewQuestionRepository iqs;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final EvaluationRepository evals;
    private final ReportRepository reports;
    private final SkillScoreRepository skills;
    private final ObjectMapper om;
    private final Clock clock;
    private final TurnRepository turns;

    public ReportService(SessionRepository sessions, InterviewQuestionRepository iqs, QuestionRepository questions,
                         AnswerRepository answers, EvaluationRepository evals, ReportRepository reports,
                         SkillScoreRepository skills, ObjectMapper om, Clock clock, TurnRepository turns) {
        this.turns = turns;
        this.sessions = sessions; this.iqs = iqs; this.questions = questions; this.answers = answers; this.evals = evals;
        this.reports = reports; this.skills = skills; this.om = om; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean exists(Long sessionId) { return reports.findBySessionId(sessionId).isPresent(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ReportData build(Long sessionId) {
        InterviewSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Interview"));
        List<InterviewQuestion> served = iqs.findBySessionIdOrderByPosition(sessionId);
        Map<Long, Question> qmap = questions.findAllById(served.stream().map(InterviewQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        List<AnswerRow> rows = new ArrayList<>();
        List<DifficultyStep> history = new ArrayList<>();
        boolean pending = false;
        boolean aiUsed = false;
        int totalTime = 0, answered = 0;
        for (InterviewQuestion iq : served) {
            Question q = qmap.get(iq.getQuestionId());
            history.add(new DifficultyStep(iq.getPosition(), iq.getDifficultyAtSelection().name(), iq.getSelectionReason()));
            CandidateAnswer a = answers.findByInterviewQuestionId(iq.getId()).orElse(null);
            AnswerEvaluation e = a == null ? null : evals.findByAnswerId(a.getId()).orElse(null);
            if (a != null) { answered++; totalTime += a.getTimeSpentSec(); }
            if (e != null && !e.isFinalized()) pending = true;
            List<InterviewTurn> tl = turns.findByInterviewQuestionIdOrderById(iq.getId());
            List<FollowUpRow> followRows = tl.stream().map(t -> new FollowUpRow(t.getKind() == TurnKind.FOLLOW_UP ? "INTERVIEWER" : "CANDIDATE", t.getSource().name(), t.getContent())).toList();
            if (tl.stream().anyMatch(t -> t.getSource() == TurnSource.AI) || (e != null && e.getEvaluator().contains("LLM"))) aiUsed = true;
            String kind = a == null || e == null || "SKIPPED".equals(e.getEvaluator()) ? "NONE"
                    : "MCQ_KEY".equals(e.getEvaluator()) ? "ANSWER_KEY" : e.getEvaluator().contains("LLM") ? "AI_ASSISTED" : "HEURISTIC";
            Double rowScore = a == null ? Double.valueOf(0.0) : (e == null ? null : e.getScore());   // boxed on purpose: null = unevaluated
            rows.add(new AnswerRow(iq.getPosition(), q.getTopic().name(), q.getSubtopic(), q.getDifficulty().name(), q.getType().name(),
                    q.getStatement(), a != null && !a.isSkipped(), a == null ? null : a.getAnswerText(), a == null ? null : a.getSelectedOption(),
                    correctAnswer(q), rowScore,
                    a == null ? Correctness.INCORRECT.name() : (e == null ? Correctness.UNEVALUATED.name() : e.getCorrectness().name()),
                    e == null ? List.of() : strings(e.getStrengths()), a == null ? List.of("Not answered") : (e == null ? List.of() : strings(e.getMissingConcepts())),
                    a == null ? "This question was not answered before the interview ended." : (e == null ? "" : e.getFeedback()),
                    e == null ? "NONE" : e.getEvaluator(), e == null || e.isFinalized(), a == null ? 0 : a.getTimeSpentSec(), q.getTimeEstimateSec(), kind, followRows));
        }

        // unanswered questions count as 0; unevaluated ones (score == null) are excluded from every average
        List<AnswerRow> scored = rows.stream().filter(r -> r.score() != null).toList();
        double overall = scored.isEmpty() ? 0 : round(scored.stream().mapToDouble(AnswerRow::score).average().orElse(0) * 100);

        Map<String, List<AnswerRow>> byTopic = scored.stream().collect(Collectors.groupingBy(AnswerRow::topic, TreeMap::new, Collectors.toList()));
        List<TopicPerf> topics = byTopic.entrySet().stream().map(en -> new TopicPerf(en.getKey(), en.getValue().size(),
                (int) en.getValue().stream().filter(AnswerRow::answered).count(),
                round(en.getValue().stream().mapToDouble(AnswerRow::score).average().orElse(0) * 100),
                (int) en.getValue().stream().filter(r -> "CORRECT".equals(r.correctness())).count())).toList();

        Map<String, List<AnswerRow>> byDiff = scored.stream().collect(Collectors.groupingBy(AnswerRow::difficulty, TreeMap::new, Collectors.toList()));
        List<DifficultyPerf> diffs = Arrays.stream(Difficulty.values()).filter(d -> byDiff.containsKey(d.name()))
                .map(d -> new DifficultyPerf(d.name(), byDiff.get(d.name()).size(),
                        round(byDiff.get(d.name()).stream().mapToDouble(AnswerRow::score).average().orElse(0) * 100))).toList();

        String strongest = topics.stream().max(Comparator.comparingDouble(TopicPerf::avgScore)).map(TopicPerf::topic).orElse(null);
        String weakest = topics.size() < 2 ? null : topics.stream().min(Comparator.comparingDouble(TopicPerf::avgScore)).map(TopicPerf::topic).orElse(null);

        List<Recommendation> recs = new ArrayList<>();
        topics.stream().filter(t -> t.avgScore() < 60).sorted(Comparator.comparingDouble(TopicPerf::avgScore)).limit(4).forEach(t -> {
            List<String> subs = scored.stream().filter(r -> r.topic().equals(t.topic()) && r.score() < 0.75).map(AnswerRow::subtopic).distinct().toList();
            recs.add(new Recommendation(t.topic(), "Scored " + Math.round(t.avgScore()) + "% across " + t.questions() + " question(s) in this interview.", subs));
        });

        return new ReportData(s.getMode().name(), aiUsed, overall, s.getQuestionCount(), answered, totalTime, pending, topics, diffs, strongest, weakest, rows, history, recs);
    }

    /** Idempotent: a second call for the same session is a no-op. Also folds the result into the long-term skill scores. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Long sessionId, ReportData data, String aiSummary) {
        if (reports.findBySessionId(sessionId).isPresent()) return;
        InterviewSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Interview"));
        InterviewReport r = new InterviewReport();
        r.setSessionId(sessionId); r.setOverallScore(data.overallScore()); r.setAiSummary(aiSummary); r.setGeneratedAt(clock.instant());
        try { r.setReportJson(om.writeValueAsString(data)); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        reports.save(r);

        for (TopicPerf t : data.topics()) {
            Topic topic = Topic.valueOf(t.topic());
            SkillScore sk = skills.findByUserIdAndTopic(s.getUserId(), topic).orElse(null);
            double measured = t.avgScore() / 100.0;
            if (sk == null) {
                sk = new SkillScore(); sk.setUserId(s.getUserId()); sk.setTopic(topic); sk.setScore(measured); sk.setAttempts(t.questions());
            } else {
                sk.setScore(Math.round(((1 - EMA_ALPHA) * sk.getScore() + EMA_ALPHA * measured) * 10000) / 10000.0);
                sk.setAttempts(sk.getAttempts() + t.questions());
            }
            sk.setUpdatedAt(clock.instant());
            skills.save(sk);
        }
    }

    @Transactional(readOnly = true)
    public ReportView view(Long userId, Long sessionId) {
        InterviewSession s = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        if (s.getStatus() == SessionStatus.IN_PROGRESS) throw ApiException.conflict("NOT_COMPLETED", "Complete the interview to see its report");
        Optional<InterviewReport> rep = reports.findBySessionId(sessionId);
        List<String> topics = s.topicList();
        if (rep.isEmpty()) {
            return new ReportView(sessionId, s.getStatus() == SessionStatus.FAILED ? "FAILED" : "GENERATING", s.getTargetRole().name(),
                    topics, s.getStartedAt(), s.getCompletedAt(), null, null, null);
        }
        try {
            ReportData data = om.readValue(rep.get().getReportJson(), new TypeReference<ReportData>() {});
            AiSummary ai = rep.get().getAiSummary() == null ? null : new AiSummary(rep.get().getAiSummary(), "AI_GENERATED");
            return new ReportView(sessionId, "READY", s.getTargetRole().name(), topics, s.getStartedAt(), s.getCompletedAt(), data, ai, rep.get().getGeneratedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report is unreadable", e);
        }
    }

    /** Facts handed to the LLM: aggregates only, no free-text from the candidate. */
    public static String facts(ReportData d) {
        StringBuilder sb = new StringBuilder("Overall score ").append(Math.round(d.overallScore())).append("%. Answered ")
                .append(d.answeredCount()).append(" of ").append(d.questionCount()).append(" questions. Topic scores: ");
        d.topics().forEach(t -> sb.append(t.topic()).append(' ').append(Math.round(t.avgScore())).append("% (").append(t.questions()).append(" q); "));
        if (d.strongestTopic() != null) sb.append("Strongest: ").append(d.strongestTopic()).append(". ");
        if (d.weakestTopic() != null) sb.append("Weakest: ").append(d.weakestTopic()).append(". ");
        return sb.toString();
    }

    private String correctAnswer(Question q) {
        if (q.getType() != QuestionType.MCQ || q.getOptions() == null) return null;
        try {
            for (var n : om.readTree(q.getOptions())) if (n.path("id").asText().equals(q.getAnswerKey())) return q.getAnswerKey() + ": " + n.path("text").asText();
        } catch (JsonProcessingException ignored) { }
        return q.getAnswerKey();
    }

    private List<String> strings(String json) {
        try { return json == null ? List.of() : om.readValue(json, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
