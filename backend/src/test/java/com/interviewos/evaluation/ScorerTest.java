package com.interviewos.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewos.domain.Correctness;
import com.interviewos.evaluation.RubricScorer.Concept;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScorerTest {
    private final RubricScorer rubric = new RubricScorer();
    private final List<Concept> concepts = List.of(
            new Concept("hashCode picks bucket", List.of("hashcode", "hash code"), 1),
            new Concept("equals resolves collisions", List.of("equals"), 1));

    @Test void fullCoverageIsCorrect() {
        var r = rubric.score("The hashCode selects a bucket and equals compares keys that collide inside it.", concepts);
        assertThat(r.score()).isEqualTo(1.0);
        assertThat(r.correctness()).isEqualTo(Correctness.CORRECT);
        assertThat(r.missing()).isEmpty();
    }

    @Test void partialCoverageListsMissingConcepts() {
        var r = rubric.score("It uses the hash code to find where the entry lives in the table.", concepts);
        assertThat(r.score()).isEqualTo(0.5);
        assertThat(r.correctness()).isEqualTo(Correctness.PARTIAL);
        assertThat(r.missing()).containsExactly("equals resolves collisions");
    }

    @Test void blankAnswerScoresZero() {
        assertThat(rubric.score("   ", concepts).score()).isZero();
    }

    @Test void keywordStuffingIsCapped() {
        assertThat(rubric.score("hashcode equals", concepts).score()).isEqualTo(0.5);
    }

    @Test void missingRubricIsUnevaluatedNotZero() {
        var r = rubric.score("anything", List.of());
        assertThat(r.score()).isNull();
        assertThat(r.correctness()).isEqualTo(Correctness.UNEVALUATED);
    }

    @Test void mcqIsKeyBasedAndCaseInsensitive() {
        var mcq = new McqScorer();
        assertThat(mcq.score("b", "B", "why").score()).isEqualTo(1.0);
        assertThat(mcq.score("A", "B", "why").score()).isZero();
        assertThat(mcq.score(null, "B", "why").correctness()).isEqualTo(Correctness.INCORRECT);
    }
}
