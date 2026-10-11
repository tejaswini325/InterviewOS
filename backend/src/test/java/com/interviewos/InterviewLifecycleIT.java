package com.interviewos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.interviewos.domain.*;
import com.interviewos.evaluation.RubricScorer.Concept;
import com.interviewos.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewLifecycleIT extends AbstractIT {
    @Autowired QuestionRepository questions;
    @Autowired InterviewQuestionRepository iqs;
    @Autowired ReportRepository reports;
    @Autowired SessionRepository sessions;
    @Autowired EvaluationRepository evals;

    static final Map<String, Object> START = Map.of("targetRole", "BACKEND_DEVELOPER",
            "topics", List.of("JAVA", "OOP", "DSA", "DBMS", "OS", "CN"), "difficulty", "MEDIUM", "durationMinutes", 60, "questionCount", 8);

    /** Builds a fully correct answer straight from the stored key / rubric (the API never exposes them). */
    Map<String, Object> correctAnswer(Long interviewQuestionId, Long sessionId) throws Exception {
        InterviewQuestion iq = iqs.findById(interviewQuestionId).orElseThrow();
        Question q = questions.findById(iq.getQuestionId()).orElseThrow();
        Map<String, Object> a = new HashMap<>();
        a.put("interviewQuestionId", interviewQuestionId);
        a.put("timeSpentSec", 12);
        if (q.getType() == QuestionType.MCQ) a.put("selectedOption", q.getAnswerKey());
        else {
            List<Concept> cs = om.readValue(q.getRubric(), new TypeReference<>() {});
            StringBuilder sb = new StringBuilder();
            for (Concept c : cs) sb.append("A complete answer covers ").append(c.keywords().get(0)).append(" and explains in detail how it works. ");
            a.put("answerText", sb.toString());
        }
        return a;
    }

    @Test void completeInterviewProducesPersistedReportHistoryAndAnalytics() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START).andExpect(status().isCreated())).get("id").asLong();

        int answered = 0;
        for (int i = 0; i < 8; i++) {
            JsonNode next = json(doPost("/api/interviews/" + id + "/next-question", token, null).andExpect(status().isOk()));
            assertThat(next.get("done").asBoolean()).isFalse();
            JsonNode q = next.get("question");
            assertThat(q.has("answerKey")).isFalse();      // keys and rubrics are never sent to the candidate
            assertThat(q.has("rubric")).isFalse();
            doPost("/api/interviews/" + id + "/answers", token, correctAnswer(q.get("interviewQuestionId").asLong(), id)).andExpect(status().isOk());
            answered++;
        }
        assertThat(json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("done").asBoolean()).isTrue();

        doPost("/api/interviews/" + id + "/complete", token, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        JsonNode report = json(doGet("/api/interviews/" + id + "/report", token).andExpect(status().isOk()));   // inline dispatch: ready immediately
        assertThat(report.get("status").asText()).isEqualTo("READY");
        assertThat(report.get("data").get("overallScore").asDouble()).isGreaterThan(90.0);
        assertThat(report.get("data").get("answers")).hasSize(answered);
        assertThat(report.get("data").get("difficultyHistory")).hasSize(answered);
        assertThat(report.has("aiSummary")).isFalse();     // no provider configured: nothing is invented

        JsonNode history = json(doGet("/api/interviews?status=COMPLETED", token));
        assertThat(history.get("content")).hasSize(1);
        assertThat(history.get("content").get(0).get("overallScore").asDouble()).isGreaterThan(90.0);
        assertThat(json(doGet("/api/interviews?topic=JAVA", token)).get("content")).hasSize(1);
        assertThat(json(doGet("/api/interviews?status=FAILED", token)).get("content")).isEmpty();

        assertThat(json(doGet("/api/analytics/overview", token)).get("completed").asInt()).isEqualTo(1);
        assertThat(json(doGet("/api/analytics/skills", token))).isNotEmpty();
    }

    @Test void difficultyRisesAfterTwoStrongAnswersAndTheReasonIsStored() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        List<Long> served = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long iq = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
            served.add(iq);
            doPost("/api/interviews/" + id + "/answers", token, correctAnswer(iq, id)).andExpect(status().isOk());
        }
        List<InterviewQuestion> rows = iqs.findBySessionIdOrderByPosition(id);
        assertThat(rows.get(0).getSelectionReason()).startsWith("Baseline difficulty MEDIUM");
        assertThat(rows.get(2).getSelectionReason()).contains("raised MEDIUM -> HARD");
        assertThat(rows.get(2).getDifficultyAtSelection()).isEqualTo(Difficulty.HARD);
    }

    @Test void difficultyDropsAfterRepeatedMistakes() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        for (int i = 0; i < 3; i++) {
            long iq = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
            doPost("/api/interviews/" + id + "/answers", token, Map.of("interviewQuestionId", iq, "skipped", true, "timeSpentSec", 1)).andExpect(status().isOk());
        }
        List<InterviewQuestion> rows = iqs.findBySessionIdOrderByPosition(id);
        assertThat(rows.get(2).getSelectionReason()).contains("lowered MEDIUM -> EASY");
    }

    @Test void questionsNeverRepeatWithinAnInterview() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            long iq = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
            assertThat(seen.add(iqs.findById(iq).orElseThrow().getQuestionId())).isTrue();
            doPost("/api/interviews/" + id + "/answers", token, Map.of("interviewQuestionId", iq, "skipped", true, "timeSpentSec", 1)).andExpect(status().isOk());
        }
    }

    @Test void duplicateSubmissionIsIdempotentAndDoesNotAdaptTwice() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        long iq = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
        Map<String, Object> answer = correctAnswer(iq, id);
        JsonNode first = json(doPost("/api/interviews/" + id + "/answers", token, answer).andExpect(status().isOk()));
        JsonNode second = json(doPost("/api/interviews/" + id + "/answers", token, answer).andExpect(status().isOk()));
        assertThat(first.get("duplicate").asBoolean()).isFalse();
        assertThat(second.get("duplicate").asBoolean()).isTrue();
        assertThat(second.get("answerId")).isEqualTo(first.get("answerId"));
        assertThat(sessions.findById(id).orElseThrow().getStrongStreak()).isEqualTo(1);   // one answer, one adaptation step
    }

    @Test void nextQuestionIsStableUntilAnswered_resumeSafe() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        long a = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
        long b = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
        assertThat(b).isEqualTo(a);
        assertThat(json(doGet("/api/interviews/" + id, token)).get("currentQuestion").get("interviewQuestionId").asLong()).isEqualTo(a);
    }

    @Test void onlyOneActiveInterviewAtATime() throws Exception {
        String token = newUser();
        doPost("/api/interviews", token, START).andExpect(status().isCreated());
        doPost("/api/interviews", token, START).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVE_SESSION_EXISTS"))
                .andExpect(jsonPath("$.extra.sessionId").exists());
    }

    @Test void candidatesCannotTouchEachOthersInterviews() throws Exception {
        String owner = newUser(), other = newUser();
        long id = json(doPost("/api/interviews", owner, START)).get("id").asLong();
        long iq = json(doPost("/api/interviews/" + id + "/next-question", owner, null)).get("question").get("interviewQuestionId").asLong();
        doGet("/api/interviews/" + id, other).andExpect(status().isNotFound());
        doPost("/api/interviews/" + id + "/next-question", other, null).andExpect(status().isNotFound());
        doPost("/api/interviews/" + id + "/answers", other, Map.of("interviewQuestionId", iq, "skipped", true, "timeSpentSec", 1)).andExpect(status().isNotFound());
        doPost("/api/interviews/" + id + "/complete", other, null).andExpect(status().isNotFound());
        doGet("/api/interviews/" + id + "/report", other).andExpect(status().isNotFound());
        assertThat(json(doGet("/api/interviews", other)).get("content")).isEmpty();
    }

    @Test void reportIsNotAvailableWhileInProgress() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        doGet("/api/interviews/" + id + "/report", token).andExpect(status().isConflict());
    }

    @Test void mcqRejectsUnknownOptionAndEmptyTextAnswer() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        long iq = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
        Question q = questions.findById(iqs.findById(iq).orElseThrow().getQuestionId()).orElseThrow();
        Map<String, Object> bad = q.getType() == QuestionType.MCQ
                ? Map.of("interviewQuestionId", iq, "selectedOption", "Z", "timeSpentSec", 1)
                : Map.of("interviewQuestionId", iq, "answerText", "   ", "timeSpentSec", 1);
        doPost("/api/interviews/" + id + "/answers", token, bad).andExpect(status().isBadRequest());
    }

    @Test void startRejectsRequestsTheBankCannotSatisfy() throws Exception {
        String token = newUser();
        doPost("/api/interviews", token, Map.of("targetRole", "JAVA_DEVELOPER", "topics", List.of("CN"), "difficulty", "EASY",
                "durationMinutes", 30, "questionCount", 30)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_QUESTIONS"));
    }

    @Test void codingQuestionsAreNotServedWhileTheSandboxIsDisabled() throws Exception {
        String token = newUser();
        assertThat(json(doGet("/api/questions/topics", token)).get("codingEnabled").asBoolean()).isFalse();
        long id = json(doPost("/api/interviews", token, START)).get("id").asLong();
        for (int i = 0; i < 8; i++) {
            JsonNode q = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question");
            assertThat(q.get("type").asText()).isNotEqualTo("CODING");
            doPost("/api/interviews/" + id + "/answers", token, Map.of("interviewQuestionId", q.get("interviewQuestionId").asLong(), "skipped", true, "timeSpentSec", 1));
        }
    }
}
