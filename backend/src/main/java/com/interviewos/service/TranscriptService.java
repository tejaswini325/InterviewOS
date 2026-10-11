package com.interviewos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider.ChatTurn;
import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.TranscriptMessage;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the conversation view from persisted rows: questions, answers and follow-up turns. */
@Service
public class TranscriptService {
    private final SessionRepository sessions;
    private final InterviewQuestionRepository iqs;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final TurnRepository turns;
    private final ObjectMapper om;

    public TranscriptService(SessionRepository sessions, InterviewQuestionRepository iqs, QuestionRepository questions,
                             AnswerRepository answers, TurnRepository turns, ObjectMapper om) {
        this.sessions = sessions; this.iqs = iqs; this.questions = questions; this.answers = answers; this.turns = turns; this.om = om;
    }

    @Transactional(readOnly = true)
    public List<TranscriptMessage> forUser(Long userId, Long sessionId) {
        sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> ApiException.notFound("Interview"));
        return messages(sessionId);
    }

    @Transactional(readOnly = true)
    public List<TranscriptMessage> messages(Long sessionId) {
        List<InterviewQuestion> served = iqs.findBySessionIdOrderByPosition(sessionId);
        Map<Long, Question> qmap = questions.findAllById(served.stream().map(InterviewQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, List<InterviewTurn>> byIq = turns.findBySessionIdOrderById(sessionId).stream()
                .collect(Collectors.groupingBy(InterviewTurn::getInterviewQuestionId));
        List<TranscriptMessage> out = new ArrayList<>();
        for (InterviewQuestion iq : served) {
            Question q = qmap.get(iq.getQuestionId());
            out.add(new TranscriptMessage("INTERVIEWER", "QUESTION", "QUESTION_BANK", q.getStatement(), iq.getPosition(), iq.getId()));
            answers.findByInterviewQuestionId(iq.getId()).ifPresent(a -> {
                if (a.isSkipped()) out.add(new TranscriptMessage("CANDIDATE", "SKIPPED", "CANDIDATE", "(skipped)", iq.getPosition(), iq.getId()));
                else out.add(new TranscriptMessage("CANDIDATE", "ANSWER", "CANDIDATE", answerText(q, a), iq.getPosition(), iq.getId()));
            });
            for (InterviewTurn t : byIq.getOrDefault(iq.getId(), List.of())) {
                boolean fu = t.getKind() == TurnKind.FOLLOW_UP;
                out.add(new TranscriptMessage(fu ? "INTERVIEWER" : "CANDIDATE", t.getKind().name(), t.getSource().name(), t.getContent(), iq.getPosition(), iq.getId()));
            }
        }
        return out;
    }

    /** Last few turns as plain chat turns for the AI prompt. */
    @Transactional(readOnly = true)
    public List<ChatTurn> recentChat(Long sessionId, int maxTurns) {
        List<TranscriptMessage> all = messages(sessionId);
        List<TranscriptMessage> tail = all.subList(Math.max(0, all.size() - maxTurns), all.size());
        return tail.stream().map(m -> new ChatTurn(m.role().equals("INTERVIEWER") ? "interviewer" : "candidate", m.content())).toList();
    }

    private String answerText(Question q, CandidateAnswer a) {
        if (q.getType() != QuestionType.MCQ) return a.getAnswerText() == null ? "" : a.getAnswerText();
        try {
            for (JsonNode n : om.readTree(q.getOptions())) if (n.path("id").asText().equalsIgnoreCase(a.getSelectedOption())) return n.path("id").asText() + ". " + n.path("text").asText();
        } catch (Exception ignored) { }
        return String.valueOf(a.getSelectedOption());
    }
}
