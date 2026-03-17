package com.aiarchitect.rag.report.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity for persisting extracted {@link com.aiarchitect.rag.report.domain.model.FinancialMetrics}.
 *
 * <p>Kept in the infrastructure layer — the domain record has no JPA annotations.
 * Mapping to/from the domain record is done in {@link ReportRepositoryAdapter}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "financial_metrics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "report_year", "report_quarter"}))
public class FinancialMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "report_year", nullable = false)
    private int year;

    @Column(name = "report_quarter", nullable = false)
    private String quarter;

    private Double revenue;
    private Double netIncome;
    private Double eps;
    private Double operatingCashFlow;
    private Double debtToEquity;
}
