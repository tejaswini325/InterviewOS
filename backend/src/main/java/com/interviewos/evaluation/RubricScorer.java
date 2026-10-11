package com.interviewos.evaluation;

import com.interviewos.domain.Correctness;
import java.util.*;

/** Deterministic rubric scoring: weighted concept coverage via keyword matching. */
public final class RubricScorer {

    /** Tunable heuristic rules (bound from interviewos.scoring.* in the application). */
    public record Rules(double correctThreshold, double partialThreshold, double stuffingCap, int minWordsPerConcept) {
        public static Rules defaults() { return new Rules(0.75, 0.40, 0.5, 3); }
    }

    private final Rules rules;

    public RubricScorer() { this(Rules.defaults()); }

    public RubricScorer(Rules rules) { this.rules = rules; }

    public record Concept(String name, List<String> keywords, double weight) {}

    public record Result(Double score, Correctness correctness, List<String> strengths, List<String> missing, String feedback) {}

    public Correctness classify(double score) {
        if (score >= rules.correctThreshold()) return Correctness.CORRECT;
        if (score >= rules.partialThreshold()) return Correctness.PARTIAL;
        return Correctness.INCORRECT;
    }

    public Result score(String answer, List<Concept> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return new Result(null, Correctness.UNEVALUATED, List.of(), List.of(), "No rubric is defined for this question.");
        }
        String text = normalize(answer);
        if (text.isBlank()) {
            return new Result(0.0, Correctness.INCORRECT, List.of(),
                    concepts.stream().map(Concept::name).toList(), "No answer was given.");
        }
        double total = concepts.stream().mapToDouble(Concept::weight).sum();
        double got = 0;
        List<String> strengths = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Concept c : concepts) {
            boolean hit = c.keywords().stream().map(RubricScorer::normalize).anyMatch(k -> !k.isBlank() && text.contains(k));
            if (hit) { got += c.weight(); strengths.add(c.name()); } else missing.add(c.name());
        }
        double score = total == 0 ? 0 : got / total;

        // Guard against keyword stuffing: a bare list of terms cannot earn more than partial credit.
        int words = text.split("\\s+").length;
        boolean stuffed = !strengths.isEmpty() && words < rules.minWordsPerConcept() * strengths.size();
        if (stuffed && score > rules.stuffingCap()) score = rules.stuffingCap();

        score = Math.round(score * 100.0) / 100.0;
        String basis = "Heuristic score: matched " + strengths.size() + " of " + concepts.size()
                + " expected concepts by keyword (this is not a semantic or AI judgement). ";
        String feedback;
        if (stuffed) feedback = "Your answer lists terms but does not explain them. Write full sentences that explain how each concept works.";
        else if (missing.isEmpty()) feedback = "Covers all expected concepts.";
        else feedback = "Revise these concepts and include them in your explanation: " + String.join(", ", missing) + ".";
        return new Result(score, classify(score), strengths, missing, basis + feedback);
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.\\s-]", " ").replaceAll("\\s+", " ").trim();
    }
}
