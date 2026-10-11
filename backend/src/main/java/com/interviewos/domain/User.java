package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "users") @Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "display_name") private String displayName;
    @Enumerated(EnumType.STRING) private Role role = Role.USER;
    @Column(name = "created_at") private Instant createdAt;
}
