package com.interviewos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name = "questions") @Getter @Setter @NoArgsConstructor
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    @Enumerated(EnumType.STRING) private Topic topic;
    private String subtopic;
    @Enumerated(EnumType.STRING) private Difficulty difficulty;
    @Enumerated(EnumType.STRING) private QuestionType type;
    private String statement;
    private String options;
    @Column(name = "answer_key") private String answerKey;
    private String explanation;
    @Column(name = "follow_ups") private String followUps;
    private String rubric;
    @Column(name = "test_cases") private String testCases;
    @Column(name = "time_estimate_sec") private int timeEstimateSec;
    private int version;
    private boolean active;
    @Column(name = "personalized_session_id") private Long personalizedSessionId;
    @Column(name = "personalized_position") private Integer personalizedPosition;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id")) @Column(name = "tag") private Set<String> tags = new TreeSet<>();
}
