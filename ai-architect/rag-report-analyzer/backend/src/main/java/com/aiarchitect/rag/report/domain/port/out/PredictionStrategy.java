package com.aiarchitect.rag.report.domain.port.out;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;

import java.util.List;

/**
 * Outbound port: strategy for generating a {@link FinancialOutlook}.
 *
 * <p>Each implementation declares its {@link PredictionMode} via {@link #mode()}.
 * {@link com.aiarchitect.rag.report.domain.service.PredictionService} builds a
 * {@code Map<PredictionMode, PredictionStrategy>} from all available beans and
 * dispatches based on the caller's requested mode.
 */
public interface PredictionStrategy {

    /**
     * Returns the prediction mode this strategy handles.
     */
    PredictionMode mode();

    /**
     * Generates a financial outlook from the supplied historical metrics.
     *
     * @param ticker            company symbol
     * @param historicalMetrics all stored periods in chronological order (year/quarter ASC)
     * @return forward-looking outlook
     */
    FinancialOutlook predict(String ticker, List<FinancialMetrics> historicalMetrics);
}
