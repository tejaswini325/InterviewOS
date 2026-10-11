package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "answer_evaluations") @Getter @Setter @NoArgsConstructor
public class AnswerEvaluation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "answer_id") private Long answerId;
    private Double score;
    @Enumerated(EnumType.STRING) private Correctness correctness;
    private String strengths;
    @Column(name = "missing_concepts") private String missingConcepts;
    private String feedback;
    private String evaluator;
    private boolean finalized;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
