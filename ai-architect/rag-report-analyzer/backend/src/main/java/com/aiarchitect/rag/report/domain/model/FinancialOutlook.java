package com.aiarchitect.rag.report.domain.model;

import java.util.List;

/**
 * Forward-looking financial outlook produced by a {@link com.aiarchitect.rag.report.domain.port.out.PredictionStrategy}.
 *
 * @param trend                  qualitative description of the financial trend
 * @param risks                  list of key risk factors
 * @param predictedRevenueRange  predicted revenue range for the next period (e.g. "$140B–$155B")
 * @param confidence             model confidence 0.0–1.0
 * @param methodology            description of the algorithm used (matches {@link PredictionMode})
 */
public record FinancialOutlook(
        String trend,
        List<String> risks,
        String predictedRevenueRange,
        Double confidence,
        String methodology
) {
}
