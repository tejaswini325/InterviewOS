package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "interview_turns") @Getter @Setter @NoArgsConstructor
public class InterviewTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "interview_question_id") private Long interviewQuestionId;
    @Enumerated(EnumType.STRING) private TurnKind kind;
    @Enumerated(EnumType.STRING) private TurnSource source;
    private String content;
    @Column(name = "created_at") private Instant createdAt;
}
