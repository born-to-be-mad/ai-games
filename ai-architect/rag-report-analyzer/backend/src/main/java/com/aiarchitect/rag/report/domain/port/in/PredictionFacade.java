package com.aiarchitect.rag.report.domain.port.in;

import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;

/**
 * Inbound port: generates a financial outlook for a given ticker using the requested strategy.
 */
public interface PredictionFacade {

    /**
     * Produces a {@link FinancialOutlook} by loading all stored {@code ticker} metrics and
     * dispatching to the appropriate {@link com.aiarchitect.rag.report.domain.port.out.PredictionStrategy}.
     *
     * @param ticker company symbol (e.g. "NVDA")
     * @param mode   algorithm to use
     * @return financial outlook with trend, risks, predicted revenue range and confidence
     * @throws IllegalArgumentException if no metrics are stored for {@code ticker}
     */
    FinancialOutlook predict(String ticker, PredictionMode mode);
}
