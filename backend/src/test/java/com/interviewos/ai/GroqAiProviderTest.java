package com.interviewos.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.ai.AiProvider.ChatTurn;
import com.interviewos.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Talks to a local stub of Groq's OpenAI-compatible endpoint: no network, no key, no cost. */
class GroqAiProviderTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "";
    private volatile String retryAfter = null;
    private volatile long delayMs = 0;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-11T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    private GroqAiProvider provider(String key, int timeoutSeconds) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            calls.incrementAndGet();
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastPath.set(ex.getRequestURI().getPath());
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            if (retryAfter != null) ex.getResponseHeaders().add("Retry-After", retryAfter);
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            try { ex.sendResponseHeaders(status, out.length); ex.getResponseBody().write(out); } catch (IOException ignored) { } finally { ex.close(); }
        });
        server.start();
        var cfg = new AppProperties.Groq(key, "openai/gpt-oss-120b", "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "low", timeoutSeconds, 60);
        return new GroqAiProvider(cfg, new ObjectMapper(), clock);
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + new ObjectMapper().valueToTree(content) + "}}]}";
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void followUpUsesTheOpenAiCompatibleEndpointAndTheConfiguredModel() throws Exception {
        var p = provider("test-key", 5);
        body = completion("Why did you pick a HashMap over a TreeMap here?");
        var out = p.followUp("Explain HashMap", List.of("hashCode"), List.of(new ChatTurn("interviewer", "Explain HashMap"), new ChatTurn("candidate", "It uses buckets")));
        assertThat(out).contains("Why did you pick a HashMap over a TreeMap here?");
        assertThat(lastPath.get()).isEqualTo("/openai/v1/chat/completions");
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get()).contains("\"model\":\"openai/gpt-oss-120b\"").contains("It uses buckets").contains("reasoning_effort");
    }

    @Test void followUpTakesOnlyTheFirstLineAndIgnoresTooShortOutput() throws Exception {
        var p = provider("k", 5);
        body = completion("What trade-offs did you consider?\nAlso here is a long unrelated rant");
        assertThat(p.followUp("q", List.of(), List.of())).contains("What trade-offs did you consider?");
        body = completion("ok");
        assertThat(p.followUp("q", List.of(), List.of())).isEmpty();
    }

    @Test void evaluationScoreIsClampedAndFieldsAreSanitised() throws Exception {
        var p = provider("k", 5);
        body = completion("Sure! {\"score\": 7.5, \"strengths\":[\"clear\"], \"missing\":[\"gc roots\"], \"feedback\":\"Good\"}");
        var e = p.evaluate("q", List.of("c"), "answer").orElseThrow();
        assertThat(e.score()).isEqualTo(1.0);
        assertThat(e.strengths()).containsExactly("clear");
        assertThat(e.missing()).containsExactly("gc roots");
    }

    @Test void personalizedQuestionsRequireTheExactCountKindsTopicsAndRubric() throws Exception {
        var p = provider("k", 5);
        body = completion("{\"questions\":["
                + "{\"kind\":\"RESUME\",\"topic\":\"PROJECT\",\"question\":\"What did you contribute to the Spring Boot project and why?\",\"concepts\":[\"contribution\",\"decision\"]},"
                + "{\"kind\":\"TECHNICAL\",\"topic\":\"JAVA\",\"question\":\"How would you protect shared state in a Java service?\",\"concepts\":[\"concurrency\",\"synchronization\"]},"
                + "{\"kind\":\"RESUME\",\"topic\":\"PROJECT\",\"question\":\"How did you measure the outcome of your backend project?\",\"concepts\":[\"measurement\",\"result\"]}]}");
        var questions = p.personalizedQuestions("Spring Boot project", "BACKEND_DEVELOPER",
                List.of("PROJECT", "JAVA"), "MEDIUM", 3).orElseThrow();
        assertThat(questions).hasSize(3);
        assertThat(questions.stream().map(AiProvider.PersonalizedQuestion::kind))
                .containsExactly("RESUME", "TECHNICAL", "RESUME");
        assertThat(lastBody.get()).contains("Spring Boot project").contains("BACKEND_DEVELOPER");

        body = completion("{\"questions\":["
                + "{\"kind\":\"TECHNICAL\",\"topic\":\"PROJECT\",\"question\":\"This is long enough to be accepted as text.\",\"concepts\":[\"a\",\"b\"]},"
                + "{\"kind\":\"RESUME\",\"topic\":\"JAVA\",\"question\":\"This is long enough to be accepted as text.\",\"concepts\":[\"a\",\"b\"]},"
                + "{\"kind\":\"RESUME\",\"topic\":\"PROJECT\",\"question\":\"This is long enough to be accepted as text.\",\"concepts\":[\"a\",\"b\"]}]}");
        assertThat(p.personalizedQuestions("resume", "BACKEND_DEVELOPER", List.of("PROJECT", "JAVA"), "MEDIUM", 3)).isEmpty();
    }

    @Test void malformedModelOutputIsRejectedNotTrusted() throws Exception {
        var p = provider("k", 5);
        body = completion("I cannot grade this.");
        assertThat(p.evaluate("q", List.of(), "a")).isEmpty();
        body = completion("{\"score\":\"high\"}");
        assertThat(p.evaluate("q", List.of(), "a")).isEmpty();
        body = "not even json";
        assertThat(p.summarize("facts")).isEmpty();
        body = "{\"choices\":[]}";
        assertThat(p.summarize("facts")).isEmpty();
    }

    @Test void rateLimitOpensACooldownSoTheProviderIsNotHammered() throws Exception {
        var p = provider("k", 5);
        status = 429; retryAfter = "20"; body = "{}";
        assertThat(p.followUp("q", List.of(), List.of())).isEmpty();
        assertThat(p.enabled()).isFalse();
        int before = calls.get();
        assertThat(p.followUp("q", List.of(), List.of())).isEmpty();
        assertThat(calls.get()).isEqualTo(before);                 // no request while cooling down

        now.set(now.get().plusSeconds(21));                         // Retry-After elapsed
        assertThat(p.enabled()).isTrue();
        status = 200; body = completion("A fresh follow-up question appears now?");
        assertThat(p.followUp("q", List.of(), List.of())).isPresent();
    }

    @Test void serverErrorsAndTimeoutsFallBackWithoutThrowing() throws Exception {
        var p = provider("k", 1);
        status = 503; body = "{}";
        assertThat(p.summarize("facts")).isEmpty();
        assertThat(p.enabled()).isFalse();

        now.set(now.get().plusSeconds(120));
        status = 200; body = completion("slow"); delayMs = 2500;
        assertThat(p.summarize("facts")).isEmpty();                 // client timeout (1 s) < server delay
        assertThat(p.enabled()).isFalse();
    }

    @Test void clientErrorsDoNotOpenACooldown() throws Exception {
        var p = provider("k", 5);
        status = 400; body = "{\"error\":\"model_decommissioned\"}";
        assertThat(p.summarize("facts")).isEmpty();
        assertThat(p.enabled()).isTrue();
    }

    @Test void withoutAKeyNothingIsEverSent() throws Exception {
        var p = provider("", 5);
        assertThat(p.enabled()).isFalse();
        assertThat(p.followUp("q", List.of(), List.of())).isEmpty();
        assertThat(p.evaluate("q", List.of(), "a")).isEmpty();
        assertThat(calls.get()).isZero();
    }
}
