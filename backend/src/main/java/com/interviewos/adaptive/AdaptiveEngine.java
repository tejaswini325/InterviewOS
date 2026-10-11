package com.interviewos.adaptive;

import com.interviewos.domain.Difficulty;
import java.util.*;

/**
 * Transparent, rule-based adaptive selection. No framework dependencies, no randomness, no LLM.
 *
 * Difficulty rules (global ladder EASY < MEDIUM < HARD):
 *  - 2 consecutive strong answers (score >= 0.75)  -> raise one level
 *  - 2 consecutive weak answers   (score <  0.40)  -> lower one level
 *  - anything in between resets both streaks
 *
 * Topic rules:
 *  - coverage first: pick the eligible topic asked the fewest times
 *  - tie-break: weakest running topic mean, then alphabetical (deterministic)
 *  - topic modifier (needs >= 2 scored attempts in that topic): mean < 0.40 -> one level easier than global,
 *    mean >= 0.85 -> one level harder than global (bounded to +-1 around the global level)
 *  - if the target difficulty has no unused question in the topic, the nearest available level is used
 */
public final class AdaptiveEngine {

    public static final double STRONG = 0.75;
    public static final double WEAK = 0.40;
    public static final double TOPIC_MASTERED = 0.85;
    public static final int STREAK_TO_CHANGE = 2;
    public static final int MIN_TOPIC_ATTEMPTS = 2;

    public record State(Difficulty current, int strongStreak, int weakStreak) {
        public static State start(Difficulty base) { return new State(base, 0, 0); }
    }

    public record Adjustment(State state, Difficulty before, Difficulty after, String reason) {}

    public record TopicStats(int asked, int scoredAttempts, double mean) {}

    public record Decision(String topic, Difficulty difficulty, String reason) {}

    public Adjustment applyResult(State s, double score) {
        int strong = s.strongStreak();
        int weak = s.weakStreak();
        if (score >= STRONG) { strong++; weak = 0; }
        else if (score < WEAK) { weak++; strong = 0; }
        else { strong = 0; weak = 0; }

        Difficulty before = s.current();
        String pct = Math.round(score * 100) + "%";
        if (strong >= STREAK_TO_CHANGE && before != Difficulty.HARD) {
            Difficulty after = before.up();
            return new Adjustment(new State(after, 0, 0), before, after,
                    "Last answer " + pct + " completed " + strong + " consecutive strong answers (>=75%): raised " + before + " -> " + after);
        }
        if (weak >= STREAK_TO_CHANGE && before != Difficulty.EASY) {
            Difficulty after = before.down();
            return new Adjustment(new State(after, 0, 0), before, after,
                    "Last answer " + pct + " completed " + weak + " consecutive weak answers (<40%): lowered " + before + " -> " + after);
        }
        return new Adjustment(new State(before, strong, weak), before, before,
                "Last answer " + pct + ": difficulty held at " + before + " (strong streak " + strong + ", weak streak " + weak + ")");
    }

    /** @param available unused question counts per topic and difficulty; empty result when nothing eligible remains */
    public Optional<Decision> choose(State s, List<String> topics, Map<String, TopicStats> stats,
                                     Map<String, Map<Difficulty, Integer>> available) {
        List<String> eligible = new ArrayList<>();
        for (String t : topics) {
            if (available.getOrDefault(t, Map.of()).values().stream().anyMatch(c -> c > 0)) eligible.add(t);
        }
        if (eligible.isEmpty()) return Optional.empty();

        TopicStats none = new TopicStats(0, 0, 0.5);
        eligible.sort(Comparator
                .comparingInt((String t) -> stats.getOrDefault(t, none).asked())
                .thenComparingDouble(t -> stats.getOrDefault(t, none).mean())
                .thenComparing(Comparator.naturalOrder()));
        String topic = eligible.get(0);
        TopicStats ts = stats.getOrDefault(topic, none);

        Difficulty target = s.current();
        StringBuilder why = new StringBuilder();
        why.append("Topic ").append(topic).append(" chosen for coverage (asked ").append(ts.asked()).append("x). ");
        if (ts.scoredAttempts() >= MIN_TOPIC_ATTEMPTS && ts.mean() < WEAK && target != Difficulty.EASY) {
            target = target.down();
            why.append("Topic mean ").append(Math.round(ts.mean() * 100)).append("% indicates a knowledge gap: one level easier than global ")
                    .append(s.current()).append(". ");
        } else if (ts.scoredAttempts() >= MIN_TOPIC_ATTEMPTS && ts.mean() >= TOPIC_MASTERED && target != Difficulty.HARD) {
            target = target.up();
            why.append("Topic mean ").append(Math.round(ts.mean() * 100)).append("% shows mastery: one level harder than global ")
                    .append(s.current()).append(". ");
        }

        Difficulty chosen = nearestAvailable(target, available.get(topic));
        if (chosen != target) {
            why.append("No unused ").append(target).append(" question left, used nearest level ").append(chosen).append(". ");
        }
        why.append("Difficulty ").append(chosen).append(".");
        return Optional.of(new Decision(topic, chosen, why.toString()));
    }

    private static Difficulty nearestAvailable(Difficulty target, Map<Difficulty, Integer> avail) {
        Difficulty best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Difficulty d : Difficulty.values()) {
            if (avail.getOrDefault(d, 0) <= 0) continue;
            int dist = Math.abs(d.level() - target.level());
            // prefer the easier level on ties so a struggling candidate is not pushed up
            if (dist < bestDist || (dist == bestDist && d.level() < best.level())) { best = d; bestDist = dist; }
        }
        return best;
    }
}
