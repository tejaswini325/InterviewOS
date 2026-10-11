package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "evaluation_jobs") @Getter @Setter @NoArgsConstructor
public class EvaluationJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) private JobKind kind;
    @Column(name = "ref_id") private Long refId;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Enumerated(EnumType.STRING) private JobStatus status;
    private int attempts;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
