package com.aiarchitect.rag.report.infrastructure.adapter.out.persistence;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.port.out.ReportRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Outbound adapter: persists {@link FinancialMetrics} via Spring Data JPA.
 *
 * <p>Upsert logic: if a record for the same (ticker, year, quarter) already exists,
 * its metric fields are updated in place; otherwise a new row is inserted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRepositoryAdapter implements ReportRepositoryPort {

    private final FinancialMetricsJpaRepository jpaRepository;

    @Override
    public FinancialMetrics save(FinancialMetrics metrics) {
        // Upsert: reuse existing id if record already exists
        FinancialMetricsEntity entity = jpaRepository
                .findByTickerAndYearAndQuarter(metrics.ticker(), metrics.year(), metrics.quarter())
                .map(existing -> {
                    existing.setRevenue(metrics.revenue());
                    existing.setNetIncome(metrics.netIncome());
                    existing.setEps(metrics.eps());
                    existing.setOperatingCashFlow(metrics.operatingCashFlow());
                    existing.setDebtToEquity(metrics.debtToEquity());
                    return existing;
                })
                .orElseGet(() -> toEntity(metrics));

        FinancialMetricsEntity saved = jpaRepository.save(entity);
        log.debug("Persisted FinancialMetrics id={} for {}/{}/{}",
                saved.getId(), saved.getTicker(), saved.getYear(), saved.getQuarter());
        return toDomain(saved);
    }

    @Override
    public Optional<FinancialMetrics> findByTickerAndYearAndQuarter(String ticker, int year, String quarter) {
        return jpaRepository.findByTickerAndYearAndQuarter(ticker, year, quarter)
                .map(this::toDomain);
    }

    @Override
    public List<FinancialMetrics> findAllByTicker(String ticker) {
        return jpaRepository.findAllByTickerOrderByYearAscQuarterAsc(ticker)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private FinancialMetrics toDomain(FinancialMetricsEntity entity) {
        return new FinancialMetrics(
                entity.getTicker(),
                entity.getYear(),
                entity.getQuarter(),
                entity.getRevenue(),
                entity.getNetIncome(),
                entity.getEps(),
                entity.getOperatingCashFlow(),
                entity.getDebtToEquity());
    }

    private FinancialMetricsEntity toEntity(FinancialMetrics metrics) {
        return FinancialMetricsEntity.builder()
                .ticker(metrics.ticker())
                .year(metrics.year())
                .quarter(metrics.quarter())
                .revenue(metrics.revenue())
                .netIncome(metrics.netIncome())
                .eps(metrics.eps())
                .operatingCashFlow(metrics.operatingCashFlow())
                .debtToEquity(metrics.debtToEquity())
                .build();
    }
}
