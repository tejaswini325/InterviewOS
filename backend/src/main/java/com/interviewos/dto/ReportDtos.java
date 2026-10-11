package com.interviewos.dto;

import java.time.Instant;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    // ---- MEASURED: computed from persisted evaluations only ----
    public record TopicPerf(String topic, int questions, int answered, double avgScore, int correct) {}
    public record DifficultyPerf(String difficulty, int questions, double avgScore) {}
    public record AnswerRow(int position, String topic, String subtopic, String difficulty, String type, String statement,
                            boolean answered, String yourAnswer, String selectedOption, String correctAnswer,
                            Double score, String correctness, List<String> strengths, List<String> missing, String feedback,
                            String evaluator, boolean finalized, int timeSpentSec, int timeEstimateSec,
                            String evaluationKind, List<FollowUpRow> followUps) {}
    /** evaluationKind: ANSWER_KEY | HEURISTIC (keyword/concept rules) | AI_ASSISTED (blended with a Groq assessment). */
    public record FollowUpRow(String role, String source, String content) {}
    public record DifficultyStep(int position, String difficulty, String reason) {}

    // ---- RECOMMENDATION: rule-based, derived from the measured data ----
    public record Recommendation(String topic, String reason, List<String> subtopics) {}

    public record ReportData(String mode, boolean aiUsed, double overallScore, int questionCount, int answeredCount, int totalTimeSec, boolean hasPendingEvaluations,
                             List<TopicPerf> topics, List<DifficultyPerf> difficulties, String strongestTopic, String weakestTopic,
                             List<AnswerRow> answers, List<DifficultyStep> difficultyHistory, List<Recommendation> recommendations) {}

    public record AiSummary(String text, String source) {}

    /** status: READY | GENERATING | FAILED. aiSummary is null unless an AI provider produced one. */
    public record ReportView(Long sessionId, String status, String targetRole, List<String> sessionTopics, Instant startedAt,
                             Instant completedAt, ReportData data, AiSummary aiSummary, Instant generatedAt) {}

    public record TrendPoint(Long sessionId, Instant date, String targetRole, double overallScore) {}
    public record Overview(long total, long completed, long inProgress, long failed, Double averageScore, Double bestScore,
                           Double latestScore, List<TrendPoint> trend, long completedThisWeek, int weeklyGoal) {}
    public record SkillView(String topic, double score, int attempts, Instant updatedAt) {}
}
