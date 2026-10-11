package com.interviewos.domain;

import java.util.List;

/** An interview mode limits which topics (and therefore which question-bank sections) can be combined. */
public enum InterviewMode {
    TECHNICAL(List.of(Topic.JAVA, Topic.OOP, Topic.DSA, Topic.DBMS, Topic.OS, Topic.CN)),
    RESUME(List.of(Topic.JAVA, Topic.OOP, Topic.DSA, Topic.DBMS, Topic.OS, Topic.CN, Topic.PROJECT)),
    DSA(List.of(Topic.DSA)),
    HR(List.of(Topic.HR)),
    PROJECT(List.of(Topic.PROJECT)),
    AI_ML(List.of(Topic.ML));

    private final List<Topic> topics;

    InterviewMode(List<Topic> topics) { this.topics = topics; }

    public List<Topic> topics() { return topics; }

    public static InterviewMode forTopic(Topic t) {
        return switch (t) {
            case DSA -> DSA;
            case HR -> HR;
            case PROJECT -> PROJECT;
            case ML -> AI_ML;
            default -> TECHNICAL;
        };
    }
}
