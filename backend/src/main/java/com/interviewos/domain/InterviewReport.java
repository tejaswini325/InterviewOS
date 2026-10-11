package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "interview_reports") @Getter @Setter @NoArgsConstructor
public class InterviewReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "overall_score") private double overallScore;
    @Column(name = "report_json") private String reportJson;
    @Column(name = "ai_summary") private String aiSummary;
    @Column(name = "generated_at") private Instant generatedAt;
}
