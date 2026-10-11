package com.interviewos.ai;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Active whenever GROQ_API_KEY is not set: the application runs entirely on its deterministic engine. */
@Component
@ConditionalOnExpression("'${interviewos.ai.groq.api-key:}'.trim() == ''")
public class NoopAiProvider implements AiProvider {
    @Override public boolean enabled() { return false; }
    @Override public Optional<AiAnswerEvaluation> evaluate(String q, List<String> c, String a) { return Optional.empty(); }
    @Override public Optional<String> followUp(String q, List<String> c, List<ChatTurn> h) { return Optional.empty(); }
    @Override public Optional<String> summarize(String facts) { return Optional.empty(); }
    @Override public Optional<List<PersonalizedQuestion>> personalizedQuestions(String resumeText, String targetRole,
                                                                                List<String> topics, String difficulty, int questionCount) {
        return Optional.empty();
    }
}
