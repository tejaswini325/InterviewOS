package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "candidate_answers") @Getter @Setter @NoArgsConstructor
public class CandidateAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "interview_question_id") private Long interviewQuestionId;
    @Column(name = "answer_text") private String answerText;
    @Column(name = "selected_option") private String selectedOption;
    private boolean skipped;
    @Column(name = "time_spent_sec") private int timeSpentSec;
    @Column(name = "submitted_at") private Instant submittedAt;
}
