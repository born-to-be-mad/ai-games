package com.aiarchitect.rag.report.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FinancialMetricsEntity}.
 */
public interface FinancialMetricsJpaRepository extends JpaRepository<FinancialMetricsEntity, Long> {

    Optional<FinancialMetricsEntity> findByTickerAndYearAndQuarter(String ticker, int year, String quarter);

    List<FinancialMetricsEntity> findAllByTickerOrderByYearAscQuarterAsc(String ticker);
}
