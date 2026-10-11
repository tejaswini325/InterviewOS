package com.interviewos.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic follow-up selection. It never calls an AI: the follow-up comes from the question's predefined
 * rules (keyed by expected concept) or, failing that, a fixed template about the first concept the answer missed.
 * Callers must label the result as rule-based.
 */
public final class RuleBasedFollowUp {

    public Optional<String> choose(List<String> missingConcepts, Map<String, String> predefined) {
        if (missingConcepts == null || missingConcepts.isEmpty()) return Optional.empty();
        for (String concept : missingConcepts) {
            String rule = predefined == null ? null : predefined.get(concept);
            if (rule != null && !rule.isBlank()) return Optional.of(rule);
        }
        return Optional.of(template(missingConcepts.get(0)));
    }

    static String template(String concept) {
        return "Your answer did not cover this point: \"" + concept + "\". Can you explain it and how it applies here?";
    }
}
