package com.aiarchitect.rag.report.domain.port.out;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: persists and queries extracted {@link FinancialMetrics}.
 *
 * <p>The concrete adapter uses Spring Data JPA + H2 (phases 1–5) and can be
 * swapped for PostgreSQL in Phase 10 without touching the domain.
 */
public interface ReportRepositoryPort {

    /**
     * Persists or updates metrics for the given ticker/year/quarter.
     */
    FinancialMetrics save(FinancialMetrics metrics);

    /**
     * Finds previously saved metrics for a specific reporting period.
     */
    Optional<FinancialMetrics> findByTickerAndYearAndQuarter(String ticker, int year, String quarter);

    /**
     * Returns all stored metrics for a ticker across all periods, ordered by year/quarter.
     */
    List<FinancialMetrics> findAllByTicker(String ticker);
}
