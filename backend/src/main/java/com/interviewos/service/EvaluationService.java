package com.interviewos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider;
import com.interviewos.domain.*;
import com.interviewos.evaluation.*;
import com.interviewos.evaluation.RubricScorer.Concept;
import com.interviewos.evaluation.RubricScorer.Result;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.*;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step evaluation:
 *  1. provisional (synchronous, deterministic) at submit time - this is what drives adaptation;
 *  2. final (asynchronous via a job) - may blend in an LLM assessment when a provider is configured.
 */
@Service
public class EvaluationService {
    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    public static final String SKIPPED_MARK = "(skipped)";
    static final double AI_WEIGHT = 0.5;   // LLM output is untrusted: never more than half of the final score

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final InterviewQuestionRepository iqs;
    private final EvaluationRepository evals;
    private final RubricScorer rubric;
    private final McqScorer mcq;
    private final CodeExecutor executor;
    private final AiProvider ai;
    private final ObjectMapper om;
    private final Clock clock;
    private final TurnRepository turns;

    public EvaluationService(QuestionRepository questions, AnswerRepository answers, InterviewQuestionRepository iqs,
                             EvaluationRepository evals, RubricScorer rubric, McqScorer mcq, CodeExecutor executor,
                             AiProvider ai, ObjectMapper om, Clock clock, TurnRepository turns) {
        this.turns = turns;
        this.questions = questions; this.answers = answers; this.iqs = iqs; this.evals = evals; this.rubric = rubric;
        this.mcq = mcq; this.executor = executor; this.ai = ai; this.om = om; this.clock = clock;
    }

    /** Runs inside the caller's transaction. */
    public AnswerEvaluation evaluateProvisional(Question q, CandidateAnswer a) {
        Result r;
        String evaluator;
        if (a.isSkipped()) {
            r = new Result(0.0, Correctness.INCORRECT, List.of(), List.of("Question was skipped"), "You skipped this question.");
            evaluator = "SKIPPED";
        } else if (q.getType() == QuestionType.MCQ) {
            r = mcq.score(a.getSelectedOption(), q.getAnswerKey(), q.getExplanation() == null ? "" : q.getExplanation());
            evaluator = "MCQ_KEY";
        } else if (q.getType() == QuestionType.CODING) {
            if (executor.available()) {
                var res = executor.run("java", a.getAnswerText(), tests(q));
                double s = res.total() == 0 ? 0 : (double) res.passed() / res.total();
                r = new Result(s, rubric.classify(s), List.of(res.passed() + "/" + res.total() + " tests passed"), List.of(), res.message());
                evaluator = "SANDBOX_TESTS";
            } else {
                r = new Result(null, Correctness.UNEVALUATED, List.of(), List.of(),
                        "Code execution is disabled in this deployment, so this answer was saved but not scored.");
                evaluator = "CODE_DISABLED";
            }
        } else {
            r = rubric.score(a.getAnswerText(), concepts(q));
            evaluator = "RUBRIC";
        }
        boolean finalized = q.getType() == QuestionType.MCQ || a.isSkipped() || r.score() == null || q.getType() == QuestionType.CODING;
        AnswerEvaluation e = new AnswerEvaluation();
        e.setAnswerId(a.getId());
        apply(e, r, evaluator, finalized);
        e.setCreatedAt(clock.instant());
        return evals.save(e);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> pendingAnswerIds(Long sessionId) {
        return evals.unfinalizedBySession(sessionId).stream().map(AnswerEvaluation::getAnswerId).toList();
    }

    /** Idempotent: a finalized evaluation is never touched again, so duplicate deliveries are harmless. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refine(Long answerId) {
        AnswerEvaluation e = evals.findByAnswerId(answerId).orElseThrow(() -> ApiException.notFound("Evaluation"));
        if (e.isFinalized()) return;
        CandidateAnswer a = answers.findById(answerId).orElseThrow(() -> ApiException.notFound("Answer"));
        InterviewQuestion iq = iqs.findById(a.getInterviewQuestionId()).orElseThrow(() -> ApiException.notFound("Question"));
        Question q = questions.findById(iq.getQuestionId()).orElseThrow(() -> ApiException.notFound("Question"));

        List<Concept> concepts = concepts(q);
        String text = combinedText(a);
        Result base = rubric.score(text, concepts);
        Optional<AiProvider.AiAnswerEvaluation> llm = ai.enabled() && base.score() != null
                ? ai.evaluate(q.getStatement(), concepts.stream().map(Concept::name).toList(), text)
                : Optional.empty();
        if (llm.isPresent()) {
            var l = llm.get();
            double blended = Math.round(((1 - AI_WEIGHT) * base.score() + AI_WEIGHT * l.score()) * 100.0) / 100.0;
            Set<String> strengths = new LinkedHashSet<>(base.strengths()); strengths.addAll(l.strengths());
            Set<String> missing = new LinkedHashSet<>(base.missing()); missing.addAll(l.missing());
            String fb = l.feedback().isBlank() ? base.feedback() : base.feedback() + " AI feedback: " + l.feedback();
            apply(e, new Result(blended, rubric.classify(blended), List.copyOf(strengths), List.copyOf(missing), fb), "RUBRIC+LLM", true);
        } else {
            apply(e, base, "RUBRIC", true);   // deterministic fallback when no provider is configured or it failed
        }
    }

    /** The answer plus the candidate's reply to the follow-up (if any): both count toward the heuristic coverage. */
    String combinedText(CandidateAnswer a) {
        String first = a.getAnswerText() == null ? "" : a.getAnswerText();
        return turns.findByInterviewQuestionIdAndKind(a.getInterviewQuestionId(), TurnKind.FOLLOW_UP_ANSWER)
                .filter(t -> !SKIPPED_MARK.equals(t.getContent())).map(t -> first + "\n" + t.getContent()).orElse(first);
    }

    /** Called inside the caller's transaction after the candidate replies to a follow-up. Scores can only go up. */
    public void rescoreAfterFollowUp(Question q, CandidateAnswer a) {
        if (a.isSkipped() || (q.getType() != QuestionType.SHORT && q.getType() != QuestionType.LONG)) return;
        evals.findByAnswerId(a.getId()).ifPresent(e -> {
            Result r = rubric.score(combinedText(a), concepts(q));
            if (r.score() == null || (e.getScore() != null && r.score() <= e.getScore())) return;
            apply(e, new Result(r.score(), r.correctness(), r.strengths(), r.missing(), r.feedback() + " Score updated after your follow-up reply."),
                    e.getEvaluator(), e.isFinalized());
        });
    }

    private void apply(AnswerEvaluation e, Result r, String evaluator, boolean finalized) {
        try {
            e.setScore(r.score());
            e.setCorrectness(r.correctness());
            e.setStrengths(om.writeValueAsString(r.strengths()));
            e.setMissingConcepts(om.writeValueAsString(r.missing()));
        } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        e.setFeedback(r.feedback() == null ? "" : r.feedback());
        e.setEvaluator(evaluator);
        e.setFinalized(finalized);
        e.setUpdatedAt(clock.instant());
    }

    private List<Concept> concepts(Question q) {
        if (q.getRubric() == null) return List.of();
        try { return om.readValue(q.getRubric(), new TypeReference<List<Concept>>() {}); }
        catch (JsonProcessingException e) { log.error("Invalid rubric JSON for question {}", q.getCode()); return List.of(); }
    }

    private List<CodeExecutor.TestCase> tests(Question q) {
        if (q.getTestCases() == null) return List.of();
        try { return om.readValue(q.getTestCases(), new TypeReference<List<CodeExecutor.TestCase>>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }
}
