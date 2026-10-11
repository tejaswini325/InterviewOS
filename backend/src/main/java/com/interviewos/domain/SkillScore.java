package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "skill_scores") @Getter @Setter @NoArgsConstructor
public class SkillScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Enumerated(EnumType.STRING) private Topic topic;
    private double score;
    private int attempts;
    @Column(name = "updated_at") private Instant updatedAt;
}
