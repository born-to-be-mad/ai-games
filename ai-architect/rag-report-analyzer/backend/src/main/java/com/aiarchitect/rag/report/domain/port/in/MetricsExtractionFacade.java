package com.aiarchitect.rag.report.domain.port.in;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.graph.MetricsGraph;

import java.util.Optional;

/**
 * Inbound port (use case): extract and query structured financial metrics.
 */
public interface MetricsExtractionFacade {

    /**
     * Retrieves relevant report chunks, extracts financial metrics using the LLM,
     * and persists the result.
     *
     * @param ticker  company ticker (e.g. "NVDA") — must match ingested documents
     * @param year    fiscal year
     * @param quarter reporting period: "Q1", "Q2", "Q3", "Q4", or "Annual"
     * @return extracted and persisted {@link FinancialMetrics}
     */
    FinancialMetrics extractAndSave(String ticker, int year, String quarter);

    /**
     * Returns previously extracted metrics for a specific period.
     */
    Optional<FinancialMetrics> getMetrics(String ticker, int year, String quarter);

    /**
     * Builds a D3-compatible knowledge graph of all metrics stored for a ticker.
     *
     * <p>Shape: {@code Company → Report → Metric} nodes + edges.
     */
    MetricsGraph getGraph(String ticker);
}
