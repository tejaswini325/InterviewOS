package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "interview_sessions") @Getter @Setter @NoArgsConstructor
public class InterviewSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "target_role") private TargetRole targetRole;
    private String topics;
    @Enumerated(EnumType.STRING) private InterviewMode mode = InterviewMode.TECHNICAL;
    @Enumerated(EnumType.STRING) @Column(name = "base_difficulty") private Difficulty baseDifficulty;
    @Enumerated(EnumType.STRING) @Column(name = "current_difficulty") private Difficulty currentDifficulty;
    @Column(name = "strong_streak") private int strongStreak;
    @Column(name = "weak_streak") private int weakStreak;
    @Column(name = "last_adjustment") private String lastAdjustment;
    @Column(name = "duration_minutes") private int durationMinutes;
    @Column(name = "question_count") private int questionCount;
    @Enumerated(EnumType.STRING) private SessionStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "deadline_at") private Instant deadlineAt;
    @Column(name = "completed_at") private Instant completedAt;
    public List<String> topicList() { return Arrays.asList(topics.split(",")); }
}
