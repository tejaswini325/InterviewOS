package com.interviewos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider;
import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.TurnView;
import com.interviewos.evaluation.RubricScorer.Concept;
import com.interviewos.evaluation.RuleBasedFollowUp;
import com.interviewos.repository.*;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Produces at most one follow-up per open-ended question.
 *  1. Groq (only when GROQ_API_KEY is set and the provider is healthy): contextual, informed by earlier turns.
 *  2. Otherwise / on any AI failure: deterministic rule-based follow-up from the question's predefined rules or concept gaps.
 * The turn's source records which engine produced it, so the UI never labels a rule-based question as AI.
 * Deliberately NOT @Transactional: the (slow) AI call must not hold a database connection or row lock.
 */
@Service
public class FollowUpService {
    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    private final SessionRepository sessions;
    private final InterviewQuestionRepository iqs;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final EvaluationRepository evals;
    private final TurnRepository turns;
    private final TranscriptService transcript;
    private final AiProvider ai;
    private final ObjectMapper om;
    private final Clock clock;
    private final RuleBasedFollowUp rules = new RuleBasedFollowUp();

    public FollowUpService(SessionRepository sessions, InterviewQuestionRepository iqs, QuestionRepository questions,
                           AnswerRepository answers, EvaluationRepository evals, TurnRepository turns, TranscriptService transcript,
                           AiProvider ai, ObjectMapper om, Clock clock) {
        this.sessions = sessions; this.iqs = iqs; this.questions = questions; this.answers = answers; this.evals = evals;
        this.turns = turns; this.transcript = transcript; this.ai = ai; this.om = om; this.clock = clock;
    }

    public Optional<TurnView> generate(Long userId, Long sessionId, Long interviewQuestionId) {
        InterviewSession s = sessions.findByIdAndUserId(sessionId, userId).orElse(null);
        if (s == null || s.getStatus() != SessionStatus.IN_PROGRESS) return Optional.empty();
        InterviewQuestion iq = iqs.findByIdAndSessionId(interviewQuestionId, sessionId).orElse(null);
        if (iq == null) return Optional.empty();

        Optional<InterviewTurn> existing = turns.findByInterviewQuestionIdAndKind(iq.getId(), TurnKind.FOLLOW_UP);
        if (existing.isPresent()) return existing.map(InterviewService::turnView);

        Question q = questions.findById(iq.getQuestionId()).orElse(null);
        if (q == null || (q.getType() != QuestionType.SHORT && q.getType() != QuestionType.LONG)) return Optional.empty();
        CandidateAnswer a = answers.findByInterviewQuestionId(iq.getId()).orElse(null);
        if (a == null || a.isSkipped()) return Optional.empty();

        List<Concept> concepts = parse(q.getRubric(), new TypeReference<List<Concept>>() {}, List.of());
        List<String> names = concepts.stream().map(Concept::name).toList();

        String text = null;
        TurnSource source = null;
        if (ai.enabled()) {
            text = ai.followUp(q.getStatement(), names, transcript.recentChat(sessionId, 8)).orElse(null);
            if (text != null) source = TurnSource.AI;
        }
        if (text == null) {   // AI off, cooling down or failed: fall back to the deterministic engine
            List<String> missing = evals.findByAnswerId(a.getId()).map(e -> parse(e.getMissingConcepts(), new TypeReference<List<String>>() {}, List.<String>of())).orElse(List.of());
            Map<String, String> predefined = parse(q.getFollowUps(), new TypeReference<Map<String, String>>() {}, Map.<String, String>of());
            text = rules.choose(missing, predefined).orElse(null);
            if (text != null) source = TurnSource.RULE_BASED;
        }
        if (text == null) return Optional.empty();

        InterviewTurn t = new InterviewTurn();
        t.setSessionId(sessionId); t.setInterviewQuestionId(iq.getId()); t.setKind(TurnKind.FOLLOW_UP);
        t.setSource(source); t.setContent(text); t.setCreatedAt(clock.instant());
        try {
            return Optional.of(InterviewService.turnView(turns.save(t)));
        } catch (DataIntegrityViolationException race) {   // a concurrent request created it first
            return turns.findByInterviewQuestionIdAndKind(iq.getId(), TurnKind.FOLLOW_UP).map(InterviewService::turnView);
        }
    }

    private <T> T parse(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return om.readValue(json, type); } catch (Exception e) { log.warn("Ignoring malformed JSON on question data"); return fallback; }
    }
}
