package com.interviewos.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewos.adaptive.AdaptiveEngine.*;
import com.interviewos.domain.Difficulty;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdaptiveEngineTest {
    private final AdaptiveEngine engine = new AdaptiveEngine();

    @Test void oneStrongAnswerHoldsDifficulty() {
        var adj = engine.applyResult(State.start(Difficulty.MEDIUM), 0.9);
        assertThat(adj.after()).isEqualTo(Difficulty.MEDIUM);
        assertThat(adj.state().strongStreak()).isEqualTo(1);
    }

    @Test void twoConsecutiveStrongAnswersRaiseDifficultyWithReason() {
        var s = engine.applyResult(State.start(Difficulty.MEDIUM), 0.9).state();
        var adj = engine.applyResult(s, 0.8);
        assertThat(adj.after()).isEqualTo(Difficulty.HARD);
        assertThat(adj.reason()).contains("raised MEDIUM -> HARD");
        assertThat(adj.state().strongStreak()).isZero();
    }

    @Test void twoConsecutiveWeakAnswersLowerDifficulty() {
        var s = engine.applyResult(State.start(Difficulty.MEDIUM), 0.1).state();
        assertThat(engine.applyResult(s, 0.2).after()).isEqualTo(Difficulty.EASY);
    }

    @Test void middlingScoreResetsStreaks() {
        var s = engine.applyResult(State.start(Difficulty.MEDIUM), 0.9).state();
        s = engine.applyResult(s, 0.5).state();
        assertThat(s.strongStreak()).isZero();
        assertThat(s.weakStreak()).isZero();
    }

    @Test void difficultyIsBoundedAtBothEnds() {
        var hard = new State(Difficulty.HARD, 1, 0);
        assertThat(engine.applyResult(hard, 1.0).after()).isEqualTo(Difficulty.HARD);
        var easy = new State(Difficulty.EASY, 0, 1);
        assertThat(engine.applyResult(easy, 0.0).after()).isEqualTo(Difficulty.EASY);
    }

    @Test void coverageBeatsDifficultyWhenPickingTopic() {
        var avail = Map.of("JAVA", Map.of(Difficulty.MEDIUM, 2), "DSA", Map.of(Difficulty.MEDIUM, 2));
        var stats = Map.of("JAVA", new TopicStats(2, 2, 0.9), "DSA", new TopicStats(0, 0, 0.5));
        var d = engine.choose(State.start(Difficulty.MEDIUM), List.of("JAVA", "DSA"), stats, avail).orElseThrow();
        assertThat(d.topic()).isEqualTo("DSA");
    }

    @Test void knowledgeGapInTopicLowersTargetDifficulty() {
        var avail = Map.of("JAVA", Map.of(Difficulty.EASY, 1, Difficulty.MEDIUM, 1));
        var stats = Map.of("JAVA", new TopicStats(2, 2, 0.1));
        var d = engine.choose(State.start(Difficulty.MEDIUM), List.of("JAVA"), stats, avail).orElseThrow();
        assertThat(d.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(d.reason()).contains("knowledge gap");
    }

    @Test void fallsBackToNearestAvailableDifficultyPreferringEasier() {
        var avail = Map.of("OS", Map.of(Difficulty.EASY, 1, Difficulty.HARD, 1));
        var d = engine.choose(State.start(Difficulty.MEDIUM), List.of("OS"), Map.of(), avail).orElseThrow();
        assertThat(d.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(d.reason()).contains("nearest level");
    }

    @Test void returnsEmptyWhenNoQuestionsRemain() {
        assertThat(engine.choose(State.start(Difficulty.EASY), List.of("OS"), Map.of(), Map.of())).isEmpty();
    }

    @Test void selectionIsDeterministic() {
        var avail = Map.of("A", Map.of(Difficulty.MEDIUM, 1), "B", Map.of(Difficulty.MEDIUM, 1));
        var first = engine.choose(State.start(Difficulty.MEDIUM), List.of("B", "A"), Map.of(), avail);
        var second = engine.choose(State.start(Difficulty.MEDIUM), List.of("A", "B"), Map.of(), avail);
        assertThat(first).isEqualTo(second);
    }
}
