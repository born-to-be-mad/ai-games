package com.aiarchitect.rag.report.prediction;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.infrastructure.adapter.out.prediction.LinearRegressionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LinearRegressionStrategy — deterministic numeric prediction")
class LinearRegressionStrategyTest {

    private LinearRegressionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LinearRegressionStrategy();
    }

    @Test
    @DisplayName("mode() returns LINEAR_REGRESSION")
    void returnsCorrectMode() {
        assertThat(strategy.mode()).isEqualTo(PredictionMode.LINEAR_REGRESSION);
    }

    @Test
    @DisplayName("predicts upward trend for consistently growing revenue")
    void predictsUptrendForGrowingRevenue() {
        List<FinancialMetrics> history = List.of(
                metrics("Q1", 10_000.0),
                metrics("Q2", 20_000.0),
                metrics("Q3", 30_000.0));

        FinancialOutlook outlook = strategy.predict("NVDA", history);

        assertThat(outlook.predictedRevenueRange()).contains("M");
        assertThat(outlook.confidence()).isGreaterThan(0.5); // strong linear fit → high R²
        assertThat(outlook.methodology()).contains("LINEAR_REGRESSION");
        assertThat(outlook.trend()).containsIgnoringCase("upward");
    }

    @Test
    @DisplayName("confidence is between 0.1 and 0.9 for any input")
    void confidenceIsClamped() {
        List<FinancialMetrics> history = List.of(
                metrics("Q1", 100.0),
                metrics("Q2", 50.0),  // noisy — not linear
                metrics("Q3", 150.0));

        FinancialOutlook outlook = strategy.predict("NVDA", history);

        assertThat(outlook.confidence()).isBetween(0.1, 0.9);
    }

    @Test
    @DisplayName("handles single period — returns prediction with low confidence")
    void handlesSinglePeriod() {
        List<FinancialMetrics> history = List.of(metrics("Q1", 10_000.0));

        FinancialOutlook outlook = strategy.predict("NVDA", history);

        assertThat(outlook).isNotNull();
        assertThat(outlook.confidence()).isLessThanOrEqualTo(0.9);
    }

    @Test
    @DisplayName("skips periods with null revenue without throwing")
    void skipsNullRevenue() {
        List<FinancialMetrics> history = List.of(
                metrics("Q1", 10_000.0),
                new FinancialMetrics("NVDA", 2024, "Q2", null, null, null, null, null),
                metrics("Q3", 30_000.0));

        FinancialOutlook outlook = strategy.predict("NVDA", history);

        assertThat(outlook).isNotNull();
        assertThat(outlook.risks()).isNotEmpty();
    }

    @Test
    @DisplayName("buildRevenueRange returns Insufficient data for non-positive prediction")
    void buildRevenueRangeHandlesNonPositive() {
        assertThat(LinearRegressionStrategy.buildRevenueRange(-100))
                .contains("Insufficient");
        assertThat(LinearRegressionStrategy.buildRevenueRange(0))
                .contains("Insufficient");
        assertThat(LinearRegressionStrategy.buildRevenueRange(Double.NaN))
                .contains("Insufficient");
    }

    private static FinancialMetrics metrics(String quarter, double revenue) {
        return new FinancialMetrics("NVDA", 2024, quarter, revenue, null, null, null, null);
    }
}
