package com.interviewos.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "interviewos")
public record AppProperties(@DefaultValue Jwt jwt, @DefaultValue Cors cors, @DefaultValue Kafka kafka,
                            @DefaultValue Redis redis, @DefaultValue Ai ai, @DefaultValue Evaluation evaluation,
                            @DefaultValue RateLimit rateLimit, @DefaultValue Scoring scoring) {
    public record Jwt(String secret, @DefaultValue("60") long expiryMinutes) {}
    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}
    public record Kafka(@DefaultValue("true") boolean enabled,
                        @DefaultValue("interviewos.evaluation.jobs") String topic,
                        @DefaultValue("interviewos.evaluation.jobs.dlq") String dlqTopic) {}
    public record Redis(@DefaultValue("true") boolean enabled) {}
    /** Optional AI. Everything is off unless GROQ_API_KEY is set; the model name lives only here. */
    public record Ai(@DefaultValue Groq groq) {}
    public record Groq(String apiKey,
                       @DefaultValue("openai/gpt-oss-120b") String model,
                       @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
                       @DefaultValue("low") String reasoningEffort,
                       @DefaultValue("15") int timeoutSeconds,
                       @DefaultValue("60") int cooldownSeconds) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    }
    public record Evaluation(@DefaultValue("3") int maxAttempts, @DefaultValue("120") int staleProcessingSeconds) {}
    public record RateLimit(@DefaultValue("10") int authRequestsPerMinute) {}
    /** Heuristic scoring rules (tunable without code changes). */
    public record Scoring(@DefaultValue("0.75") double correctThreshold, @DefaultValue("0.40") double partialThreshold,
                          @DefaultValue("0.5") double stuffingCap, @DefaultValue("3") int minWordsPerConcept) {}
}
