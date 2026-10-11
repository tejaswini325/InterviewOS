package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "interview_questions") @Getter @Setter @NoArgsConstructor
public class InterviewQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "question_id") private Long questionId;
    private int position;
    @Enumerated(EnumType.STRING) @Column(name = "difficulty_at_selection") private Difficulty difficultyAtSelection;
    @Column(name = "selection_reason") private String selectionReason;
    @Column(name = "presented_at") private Instant presentedAt;
}
