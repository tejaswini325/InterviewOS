package com.interviewos.dto;

import com.interviewos.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() {}

    /** mode is optional for backward compatibility; null means TECHNICAL. */
    public record StartRequest(@NotNull TargetRole targetRole, InterviewMode mode,
                               @NotEmpty @Size(max = 7) List<Topic> topics,
                               @NotNull Difficulty difficulty,
                               @Min(5) @Max(180) int durationMinutes,
                               @Min(3) @Max(30) int questionCount) {}

    public record OptionView(String id, String text) {}
    public record TestCaseView(String input, String expected) {}

    /** Never contains the answer key, explanation or rubric. */
    public record QuestionView(Long interviewQuestionId, Long questionId, boolean bookmarked, int position, Topic topic, String subtopic, Difficulty difficulty,
                               QuestionType type, String statement, List<OptionView> options, List<TestCaseView> sampleTests,
                               List<String> tags, int timeEstimateSec, Instant presentedAt) {}

    public record SessionView(Long id, SessionStatus status, TargetRole targetRole, InterviewMode mode, List<Topic> topics,
                              Difficulty baseDifficulty, Difficulty currentDifficulty, int durationMinutes, int questionCount,
                              int answeredCount, Instant startedAt, Instant deadlineAt, Instant completedAt, long remainingSeconds,
                              QuestionView currentQuestion, TurnView pendingFollowUp, boolean aiAvailable) {}

    /** source: AI | RULE_BASED | CANDIDATE. Rule-based turns are never presented as AI. */
    public record TurnView(Long id, Long interviewQuestionId, String kind, String source, String content) {}

    public record FollowUpAnswerRequest(@NotNull Long interviewQuestionId, @Size(max = 5000) String answerText, boolean skipped) {}

    /** role: INTERVIEWER | CANDIDATE. kind: QUESTION | ANSWER | SKIPPED | FOLLOW_UP | FOLLOW_UP_ANSWER. source: QUESTION_BANK | AI | RULE_BASED | CANDIDATE. */
    public record TranscriptMessage(String role, String kind, String source, String content, Integer position, Long interviewQuestionId) {}

    public record BookmarkView(Long questionId, Topic topic, String subtopic, Difficulty difficulty, QuestionType type, String statement, Instant createdAt) {}

    /** basis: MEASURED (from your skill scores) | BOOKMARKS | NEW_TOPIC. */
    public record PracticeSuggestion(String title, String reason, String basis, InterviewMode mode, List<Topic> topics, Difficulty difficulty) {}

    public record NextQuestionResponse(boolean done, String doneReason, QuestionView question, int answeredCount, int questionCount) {}

    public record AnswerRequest(@NotNull Long interviewQuestionId,
                                @Size(max = 10000) String answerText,
                                @Size(max = 10) String selectedOption,
                                boolean skipped,
                                @Min(0) @Max(86400) int timeSpentSec) {}

    public record AnswerAck(Long answerId, boolean duplicate, int answeredCount, int questionCount, TurnView followUp) {}

    public record SessionSummary(Long id, SessionStatus status, TargetRole targetRole, InterviewMode mode, List<Topic> topics, Difficulty baseDifficulty,
                                 int questionCount, int answeredCount, Instant startedAt, Instant completedAt, Double overallScore) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record JobView(Long id, String kind, Long sessionId, String status, int attempts, String lastError, Instant updatedAt) {}
}
