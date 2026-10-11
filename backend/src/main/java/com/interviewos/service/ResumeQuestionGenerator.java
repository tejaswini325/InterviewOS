package com.interviewos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider;
import com.interviewos.ai.AiProvider.PersonalizedQuestion;
import com.interviewos.domain.Difficulty;
import com.interviewos.domain.TargetRole;
import com.interviewos.domain.Topic;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeQuestionGenerator {
    private static final Logger log = LoggerFactory.getLogger(ResumeQuestionGenerator.class);
    private static final List<String> RESUME_HINTS = List.of(
            "Spring Boot", "Kubernetes", "PostgreSQL", "machine learning", "microservices", "Hibernate",
            "Kafka", "Redis", "Docker", "AWS", "React", "Java", "REST API", "SQL", "JUnit");

    private final AiProvider ai;
    private final ObjectMapper mapper;

    public ResumeQuestionGenerator(AiProvider ai, ObjectMapper mapper) {
        this.ai = ai;
        this.mapper = mapper;
    }

    /** The optional model call happens outside the interview-start transaction. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<PersonalizedQuestion> generate(String resumeText, TargetRole role, List<Topic> topics,
                                               Difficulty difficulty, int questionCount) {
        List<String> topicNames = topics.stream().map(Enum::name).toList();
        if (ai.enabled()) {
            Optional<List<PersonalizedQuestion>> generated = ai.personalizedQuestions(
                    resumeText, role.name(), topicNames, difficulty.name(), questionCount);
            if (generated.isPresent()) return alternate(generated.get());
            log.warn("Personalized question generation fell back to deterministic templates");
        }
        return fallback(resumeText, role, topics, questionCount);
    }

    public String rubricJson(List<String> concepts) {
        List<com.interviewos.evaluation.RubricScorer.Concept> rubric = concepts.stream()
                .map(name -> new com.interviewos.evaluation.RubricScorer.Concept(name, List.of(name), 1.0))
                .toList();
        try {
            return mapper.writeValueAsString(rubric);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize personalized question rubric", e);
        }
    }

    private List<PersonalizedQuestion> fallback(String resumeText, TargetRole role, List<Topic> topics, int count) {
        int resumeCount = (count + 1) / 2;
        int technicalCount = count / 2;
        List<PersonalizedQuestion> resumeQuestions = new ArrayList<>();
        List<PersonalizedQuestion> technicalQuestions = new ArrayList<>();
        List<String> hints = resumeHints(resumeText);
        Topic resumeTopic = topics.contains(Topic.PROJECT) ? Topic.PROJECT : topics.stream().filter(t -> t != Topic.PROJECT).findFirst().orElseThrow();
        String roleName = roleLabel(role);

        for (int i = 0; i < resumeCount; i++) {
            String evidence = hints.get(i % hints.size());
            String context = evidence.equals("the work described in your resume") ? evidence : "your resume's mention of " + evidence;
            resumeQuestions.add(new PersonalizedQuestion("RESUME", resumeTopic.name(),
                    "Your resume includes " + context + ". For a " + roleName + " position, explain what you personally contributed, "
                            + "one technical decision you made, and the result.",
                    List.of("personal contribution", "technical decision", "result")));
        }

        List<Topic> technicalTopics = topics.stream().filter(t -> t != Topic.PROJECT).toList();
        for (int i = 0; i < technicalCount; i++) {
            Topic topic = technicalTopics.get(i % technicalTopics.size());
            technicalQuestions.add(technicalQuestion(topic, roleName));
        }
        return alternate(resumeQuestions, technicalQuestions);
    }

    private static PersonalizedQuestion technicalQuestion(Topic topic, String role) {
        return switch (topic) {
            case JAVA -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", how would you make a Java service safe under concurrent requests, and what would you test?",
                    List.of("concurrency", "shared state", "thread safety"));
            case OOP -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", describe how you would model a changing business workflow using object-oriented design while keeping components testable.",
                    List.of("encapsulation", "polymorphism", "testability"));
            case DSA -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", how would you find duplicate records efficiently in a large stream, and how would you explain the time and space trade-offs?",
                    List.of("hash set", "time complexity", "space complexity"));
            case DBMS -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", how would you investigate and improve a slow database query without compromising transactional correctness?",
                    List.of("query plan", "index", "transaction"));
            case OS -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", explain when you would use multiple threads rather than multiple processes for a workload, including the main trade-offs.",
                    List.of("shared memory", "isolation", "synchronization"));
            case CN -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", trace a client request to a REST service and explain how HTTP status codes, timeouts, and retries should be handled.",
                    List.of("HTTP", "status code", "timeout"));
            case ML -> new PersonalizedQuestion("TECHNICAL", topic.name(),
                    "As a " + role + ", how would you detect and reduce overfitting, and which validation strategy would you use?",
                    List.of("overfitting", "regularization", "validation"));
            case HR, PROJECT -> throw new IllegalArgumentException("Not a technical resume topic: " + topic);
        };
    }

    private static List<String> resumeHints(String resumeText) {
        String lower = resumeText.toLowerCase(Locale.ROOT);
        List<String> matches = RESUME_HINTS.stream().filter(h -> lower.contains(h.toLowerCase(Locale.ROOT))).toList();
        return matches.isEmpty() ? List.of("the work described in your resume") : matches;
    }

    private static String roleLabel(TargetRole role) {
        return switch (role) {
            case JAVA_DEVELOPER -> "Java Developer";
            case BACKEND_DEVELOPER -> "Backend Developer";
            case SOFTWARE_ENGINEER -> "Software Engineer";
        };
    }

    private static List<PersonalizedQuestion> alternate(List<PersonalizedQuestion> questions) {
        return alternate(questions.stream().filter(q -> q.kind().equals("RESUME")).toList(),
                questions.stream().filter(q -> q.kind().equals("TECHNICAL")).toList());
    }

    private static List<PersonalizedQuestion> alternate(List<PersonalizedQuestion> resume, List<PersonalizedQuestion> technical) {
        List<PersonalizedQuestion> out = new ArrayList<>(resume.size() + technical.size());
        for (int i = 0; i < resume.size() || i < technical.size(); i++) {
            if (i < resume.size()) out.add(resume.get(i));
            if (i < technical.size()) out.add(technical.get(i));
        }
        return List.copyOf(out);
    }
}
