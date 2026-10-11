package com.interviewos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;

/** No GROQ_API_KEY in the test profile: everything below runs on the deterministic engine and must say so. */
class ModesFollowUpIT extends AbstractIT {

    static Map<String, Object> hrStart(int count) {
        return Map.of("targetRole", "SOFTWARE_ENGINEER", "mode", "HR", "topics", List.of("HR"), "difficulty", "MEDIUM", "durationMinutes", 30, "questionCount", count);
    }

    private long start(String token, int count) throws Exception {
        return json(doPost("/api/interviews", token, hrStart(count)).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode answer(String token, long id, long iq, String text) throws Exception {
        return json(doPost("/api/interviews/" + id + "/answers", token, Map.of("interviewQuestionId", iq, "answerText", text, "timeSpentSec", 20)).andExpect(status().isOk()));
    }

    private long nextQuestion(String token, long id) throws Exception {
        return json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question").get("interviewQuestionId").asLong();
    }

    @Test void topicsMustBelongToTheChosenMode() throws Exception {
        String token = newUser();
        doPost("/api/interviews", token, Map.of("targetRole", "SOFTWARE_ENGINEER", "mode", "HR", "topics", List.of("JAVA"), "difficulty", "EASY", "durationMinutes", 30, "questionCount", 3))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOPICS_FOR_MODE"));
    }

    @Test void modesExposeTheirOwnQuestionBankSections() throws Exception {
        String token = newUser();
        JsonNode topics = json(doGet("/api/questions/topics", token)).get("topics");
        Map<String, Integer> totals = new HashMap<>();
        topics.forEach(t -> totals.put(t.get("topic").asText(), t.get("total").asInt()));
        assertThat(totals.get("HR")).isGreaterThanOrEqualTo(3);
        assertThat(totals.get("PROJECT")).isGreaterThanOrEqualTo(3);
        assertThat(totals.get("ML")).isGreaterThanOrEqualTo(3);
    }

    @Test void vagueAnswerGetsALabelledRuleBasedFollowUpAndTheReplyImprovesTheHeuristicScore() throws Exception {
        String token = newUser();
        long id = start(token, 3);
        long iq = nextQuestion(token, id);

        JsonNode ack = answer(token, id, iq, "I think it went fine in the end and everybody was happy with how things worked out overall.");
        JsonNode fu = ack.get("followUp");
        assertThat(fu).isNotNull();
        assertThat(fu.get("source").asText()).isEqualTo("RULE_BASED");          // never presented as AI
        assertThat(fu.get("content").asText()).isNotBlank();

        JsonNode view = json(doGet("/api/interviews/" + id, token));
        assertThat(view.get("aiAvailable").asBoolean()).isFalse();
        assertThat(view.get("pendingFollowUp").get("id").asLong()).isEqualTo(fu.get("id").asLong());   // resumable

        // answering the same question again does not create a second follow-up
        assertThat(answer(token, id, iq, "ignored duplicate").get("followUp").get("id")).isEqualTo(fu.get("id"));

        JsonNode reply = json(doPost("/api/interviews/" + id + "/follow-up", token, Map.of("interviewQuestionId", iq,
                "answerText", "The situation was a team project and my goal was to finish it. I decided to talk to them, I listened, I suggested a plan, and as a result the issue was resolved and the delivered work improved. I made a mistake and I took responsibility, I fixed it and learned a lesson. I had to prioritize the most important scope, communicate with my manager, and I worked hard and met the deadline on time.")).andExpect(status().isOk()));
        assertThat(reply.get("source").asText()).isEqualTo("CANDIDATE");
        assertThat(json(doGet("/api/interviews/" + id, token)).has("pendingFollowUp")).isFalse();

        List<String> kinds = new ArrayList<>();
        json(doGet("/api/interviews/" + id + "/transcript", token)).forEach(m -> kinds.add(m.get("kind").asText() + ":" + m.get("source").asText()));
        assertThat(kinds).containsSubsequence("QUESTION:QUESTION_BANK", "ANSWER:CANDIDATE", "FOLLOW_UP:RULE_BASED", "FOLLOW_UP_ANSWER:CANDIDATE");

        doPost("/api/interviews/" + id + "/complete", token, null).andExpect(status().isOk());
        JsonNode report = json(doGet("/api/interviews/" + id + "/report", token));
        JsonNode row = report.get("data").get("answers").get(0);
        assertThat(report.get("data").get("mode").asText()).isEqualTo("HR");
        assertThat(report.get("data").get("aiUsed").asBoolean()).isFalse();
        assertThat(row.get("evaluationKind").asText()).isEqualTo("HEURISTIC");   // keyword/concept rules, not "AI"
        assertThat(row.get("followUps")).hasSize(2);
        assertThat(row.get("score").asDouble()).isGreaterThan(0.5);               // the follow-up reply covered more concepts
        assertThat(row.get("feedback").asText()).contains("Heuristic score");
    }

    @Test void skippedQuestionsGetNoFollowUp() throws Exception {
        String token = newUser();
        long id = start(token, 3);
        long iq = nextQuestion(token, id);
        JsonNode ack = json(doPost("/api/interviews/" + id + "/answers", token, Map.of("interviewQuestionId", iq, "skipped", true, "timeSpentSec", 1)).andExpect(status().isOk()));
        assertThat(ack.has("followUp")).isFalse();
    }

    @Test void mcqQuestionsDoNotGetFollowUps() throws Exception {
        String token = newUser();
        long id = json(doPost("/api/interviews", token, Map.of("targetRole", "SOFTWARE_ENGINEER", "mode", "AI_ML", "topics", List.of("ML"), "difficulty", "EASY", "durationMinutes", 30, "questionCount", 8)).andExpect(status().isCreated())).get("id").asLong();
        for (int i = 0; i < 8; i++) {
            JsonNode q = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question");
            boolean mcq = "MCQ".equals(q.get("type").asText());
            JsonNode ack = json(doPost("/api/interviews/" + id + "/answers", token, mcq
                    ? Map.of("interviewQuestionId", q.get("interviewQuestionId").asLong(), "selectedOption", "A", "timeSpentSec", 3)
                    : Map.of("interviewQuestionId", q.get("interviewQuestionId").asLong(), "answerText", "a short vague answer without much detail at all", "timeSpentSec", 3)).andExpect(status().isOk()));
            if (mcq) assertThat(ack.has("followUp")).isFalse();
        }
    }

    @Test void otherCandidatesCannotReadOrAnswerTheConversation() throws Exception {
        String owner = newUser(), other = newUser();
        long id = start(owner, 3);
        long iq = nextQuestion(owner, id);
        answer(owner, id, iq, "A reasonably long answer about my background and what I have studied so far.");
        doGet("/api/interviews/" + id + "/transcript", other).andExpect(status().isNotFound());
        doPost("/api/interviews/" + id + "/follow-up", other, Map.of("interviewQuestionId", iq, "answerText", "x")).andExpect(status().isNotFound());
    }

    @Test void bookmarksAreIdempotentPerUserAndShownOnQuestions() throws Exception {
        String token = newUser(), other = newUser();
        long id = start(token, 3);
        JsonNode q = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question");
        long qid = q.get("questionId").asLong();
        assertThat(q.get("bookmarked").asBoolean()).isFalse();

        doPost("/api/bookmarks/" + qid, token, null).andExpect(status().isNoContent());
        doPost("/api/bookmarks/" + qid, token, null).andExpect(status().isNoContent());
        assertThat(json(doGet("/api/bookmarks", token))).hasSize(1);
        assertThat(json(doGet("/api/bookmarks", other))).isEmpty();
        assertThat(json(doGet("/api/interviews/" + id, token)).get("currentQuestion").get("bookmarked").asBoolean()).isTrue();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/bookmarks/" + qid).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(json(doGet("/api/bookmarks", token))).isEmpty();
        doPost("/api/bookmarks/999999", token, null).andExpect(status().isNotFound());
    }

    @Test void practiceSuggestionsStateWhatTheyAreBasedOn() throws Exception {
        String token = newUser();
        JsonNode s = json(doGet("/api/practice/suggestions", token));
        assertThat(s).isNotEmpty();
        s.forEach(x -> assertThat(x.get("basis").asText()).isIn("MEASURED", "BOOKMARKS", "NEW_TOPIC"));
        assertThat(s.get(0).get("basis").asText()).isEqualTo("NEW_TOPIC");   // brand-new user: no measured data to base anything on
    }

    @Test void historyCanBeFilteredByMode() throws Exception {
        String token = newUser();
        start(token, 3);
        assertThat(json(doGet("/api/interviews?mode=HR", token)).get("content")).hasSize(1);
        assertThat(json(doGet("/api/interviews?mode=AI_ML", token)).get("content")).isEmpty();
    }
}
