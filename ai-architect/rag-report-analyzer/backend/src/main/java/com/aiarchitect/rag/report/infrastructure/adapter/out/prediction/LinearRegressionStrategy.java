package com.aiarchitect.rag.report.infrastructure.adapter.out.prediction;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.out.PredictionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prediction strategy: fits a linear regression on historical revenue and extrapolates one period ahead.
 *
 * <p>Uses Apache Commons Math {@link SimpleRegression}. Period index (0, 1, 2, …) is the independent variable;
 * revenue in millions USD is the dependent variable. Null revenue periods are skipped.
 *
 * <p>Confidence is derived from R² — the coefficient of determination — clamped to [0.1, 0.9].
 * A perfect linear fit gives R²=1.0 → confidence=0.9; no linear relationship gives R²=0 → confidence=0.1.
 *
 * <p>No LLM is called. This strategy is fully deterministic given the same input.
 */
@Slf4j
@Component
public class LinearRegressionStrategy implements PredictionStrategy {

    @Override
    public PredictionMode mode() {
        return PredictionMode.LINEAR_REGRESSION;
    }

    @Override
    public FinancialOutlook predict(String ticker, List<FinancialMetrics> historicalMetrics) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < historicalMetrics.size(); i++) {
            Double revenue = historicalMetrics.get(i).revenue();
            if (revenue != null) {
                regression.addData(i, revenue);
            }
        }

        int nextPeriod = historicalMetrics.size();
        double predicted = regression.predict(nextPeriod);
        double slope = regression.getSlope();
        double r2 = regression.getRSquare();

        // Clamp confidence: R²=1 → 0.9, R²=0 → 0.1
        double confidence = Double.isNaN(r2) ? 0.1 : Math.max(0.1, Math.min(0.9, r2));

        String trend = buildTrendDescription(ticker, slope, historicalMetrics.size(), r2);
        String revenueRange = buildRevenueRange(predicted);

        log.debug("Linear regression for {}: slope={:.2f}, R²={:.3f}, predicted={:.0f}M",
                ticker, slope, r2, predicted);

        return new FinancialOutlook(
                trend,
                List.of(
                        "Linear regression assumes a constant growth rate — actual results may deviate",
                        "Small sample size (n=" + historicalMetrics.size() + ") reduces statistical reliability",
                        "Model does not account for macro-economic shocks or structural business changes"
                ),
                revenueRange,
                confidence,
                "LINEAR_REGRESSION: Apache Commons Math SimpleRegression (R²=%.3f)".formatted(r2));
    }

    private static String buildTrendDescription(String ticker, double slope, int periods, double r2) {
        String direction = slope > 0 ? "upward" : "downward";
        String fit = Double.isNaN(r2) ? "insufficient data for R²"
                : "R²=%.3f (%s fit)".formatted(r2, r2 >= 0.8 ? "strong" : r2 >= 0.5 ? "moderate" : "weak");
        return "%s shows a %s linear revenue trend over %d periods (%s). "
                .formatted(ticker, direction, periods, fit)
                + "Slope: %+.1f$M per period.".formatted(slope);
    }

    /**
     * Returns a ±10 % revenue range string for the predicted value.
     * If the prediction is negative (can happen with noisy data), returns "Insufficient data".
     */
    public static String buildRevenueRange(double predicted) {
        if (predicted <= 0 || Double.isNaN(predicted) || Double.isInfinite(predicted)) {
            return "Insufficient data for revenue range prediction";
        }
        double low = predicted * 0.9;
        double high = predicted * 1.1;
        return "$%.0fM–$%.0fM".formatted(low, high);
    }
}
