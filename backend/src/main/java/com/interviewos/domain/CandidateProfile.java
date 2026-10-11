package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "candidate_profiles") @Getter @Setter @NoArgsConstructor
public class CandidateProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "target_role") private TargetRole targetRole;
    @Enumerated(EnumType.STRING) @Column(name = "experience_level") private ExperienceLevel experienceLevel;
    @Column(name = "skill_preferences") private String skillPreferences = "";
    @Column(name = "resume_text", columnDefinition = "TEXT") private String resumeText;
    @Column(name = "resume_filename") private String resumeFilename;
    @Column(name = "resume_updated_at") private Instant resumeUpdatedAt;
    @Column(name = "weekly_goal") private int weeklyGoal = 3;
    @Column(name = "updated_at") private Instant updatedAt;
}
