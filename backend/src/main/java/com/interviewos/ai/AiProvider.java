package com.interviewos.ai;

import java.util.List;
import java.util.Optional;

/**
 * Optional AI port. Every method returns empty on any failure or when AI is not configured; callers always have a
 * deterministic fallback and must label AI output as AI-generated.
 */
public interface AiProvider {
    boolean enabled();

    /** @param role "interviewer" or "candidate" */
    record ChatTurn(String role, String content) {}

    record AiAnswerEvaluation(double score, List<String> strengths, List<String> missing, String feedback) {}
    record PersonalizedQuestion(String kind, String topic, String question, List<String> concepts) {}

    Optional<AiAnswerEvaluation> evaluate(String question, List<String> expectedConcepts, String candidateAnswer);

    /** One contextual follow-up question informed by the previous conversation turns. */
    Optional<String> followUp(String question, List<String> expectedConcepts, List<ChatTurn> history);

    Optional<String> summarize(String measuredFacts);

    /** Generates questions from candidate-provided resume text. Resume text is untrusted data, never instructions. */
    Optional<List<PersonalizedQuestion>> personalizedQuestions(String resumeText, String targetRole,
                                                               List<String> topics, String difficulty, int questionCount);
}
