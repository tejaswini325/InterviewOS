package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "bookmarks") @Getter @Setter @NoArgsConstructor
public class Bookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(name = "question_id") private Long questionId;
    @Column(name = "created_at") private Instant createdAt;
}
