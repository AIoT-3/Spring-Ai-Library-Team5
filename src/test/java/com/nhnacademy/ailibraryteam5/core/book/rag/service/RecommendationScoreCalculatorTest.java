package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecommendationScoreCalculatorTest {

    private final RecommendationScoreCalculator calculator = new RecommendationScoreCalculator();

    @Test
    void calculateDoesNotHalveRrfForTopSingleSourceCandidate() {
        double topVectorRrfScore = 1.0 / 61.0;
        double topKeywordAndVectorRrfScore = (1.0 / 61.0) + (1.0 / 62.0);

        Double score = calculator.calculate(
                0.55,
                topVectorRrfScore,
                topKeywordAndVectorRrfScore
        );

        assertThat(score).isCloseTo(91.0, within(0.0001));
    }

    @Test
    void calculateCapsRrfPercentAtOneHundred() {
        double topKeywordAndVectorRrfScore = (1.0 / 61.0) + (1.0 / 62.0);

        Double score = calculator.calculate(
                0.50,
                topKeywordAndVectorRrfScore,
                topKeywordAndVectorRrfScore
        );

        assertThat(score).isCloseTo(90.0, within(0.0001));
    }
}
