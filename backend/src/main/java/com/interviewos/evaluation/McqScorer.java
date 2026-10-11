package com.interviewos.evaluation;

import com.interviewos.domain.Correctness;
import java.util.List;

public final class McqScorer {
    public RubricScorer.Result score(String selected, String key, String explanation) {
        if (selected == null || selected.isBlank()) {
            return new RubricScorer.Result(0.0, Correctness.INCORRECT, List.of(), List.of("Correct option " + key),
                    "No option was selected.");
        }
        boolean ok = selected.trim().equalsIgnoreCase(key.trim());
        return ok
                ? new RubricScorer.Result(1.0, Correctness.CORRECT, List.of("Chose the correct option"), List.of(), explanation)
                : new RubricScorer.Result(0.0, Correctness.INCORRECT, List.of(), List.of("Correct option was " + key), explanation);
    }
}
