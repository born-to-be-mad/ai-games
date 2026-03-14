package com.aiarchitect.rag.report.domain.model;

/**
 * Extracted financial metrics for a specific company reporting period.
 *
 * <p>All monetary values are in millions USD. {@code null} means the metric
 * was not found in the report excerpts provided to the LLM.
 *
 * @param ticker             company ticker symbol (e.g. "NVDA")
 * @param year               fiscal year (e.g. 2025)
 * @param quarter            reporting period: "Q1", "Q2", "Q3", "Q4", or "Annual"
 * @param revenue            total revenue, millions USD
 * @param netIncome          net income, millions USD
 * @param eps                diluted earnings per share
 * @param operatingCashFlow  operating cash flow, millions USD
 * @param debtToEquity       total debt / total equity ratio
 */
public record FinancialMetrics(
        String ticker,
        int year,
        String quarter,
        Double revenue,
        Double netIncome,
        Double eps,
        Double operatingCashFlow,
        Double debtToEquity
) {
}
