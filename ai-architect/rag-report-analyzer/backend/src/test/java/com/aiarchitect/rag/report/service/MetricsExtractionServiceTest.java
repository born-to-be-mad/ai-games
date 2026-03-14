package com.aiarchitect.rag.report.service;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.graph.MetricsGraph;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.MetricsExtractionPort;
import com.aiarchitect.rag.report.domain.port.out.ReportRepositoryPort;
import com.aiarchitect.rag.report.domain.service.MetricsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link MetricsExtractionService}.
 *
 * <p>Verifies the orchestration: metadata filter is forwarded to the store,
 * the extraction port receives the retrieved chunks, and the result is persisted.
 */
@DisplayName("MetricsExtractionService — orchestration")
class MetricsExtractionServiceTest {

    private DocumentStorePort documentStorePort;
    private MetricsExtractionPort metricsExtractionPort;
    private ReportRepositoryPort reportRepositoryPort;
    private MetricsExtractionService service;

    @BeforeEach
    void setUp() {
        documentStorePort = mock(DocumentStorePort.class);
        metricsExtractionPort = mock(MetricsExtractionPort.class);
        reportRepositoryPort = mock(ReportRepositoryPort.class);
        service = new MetricsExtractionService(documentStorePort, metricsExtractionPort, reportRepositoryPort);
    }

    @Test
    @DisplayName("extractAndSave: searches with financial keyword query + ticker/year filter")
    void searchUsesFinancialKeywordQueryWithFilter() {
        FinancialMetrics stub = metrics("NVDA", 2025, "Annual", 130497.0, 72880.0);
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap())).thenReturn(List.of());
        when(metricsExtractionPort.extract(any(), anyInt(), any(), anyList())).thenReturn(stub);
        when(reportRepositoryPort.save(any())).thenReturn(stub);

        service.extractAndSave("NVDA", 2025, "Annual");

        verify(documentStorePort).similaritySearch(
                eq(MetricsExtractionService.METRICS_QUERY),
                eq(MetricsExtractionService.METRICS_TOP_K),
                eq(Map.of("ticker", "NVDA", "year", 2025)));
    }

    @Test
    @DisplayName("extractAndSave: extracted metrics are persisted and returned")
    void extractedMetricsArePersistedAndReturned() {
        Document chunk = new Document("Revenue was $130B.", Map.of("ticker", "NVDA", "year", 2025));
        FinancialMetrics extracted = metrics("NVDA", 2025, "Annual", 130497.0, 72880.0);
        FinancialMetrics saved = metrics("NVDA", 2025, "Annual", 130497.0, 72880.0);

        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap()))
                .thenReturn(List.of(chunk));
        when(metricsExtractionPort.extract("NVDA", 2025, "Annual", List.of(chunk)))
                .thenReturn(extracted);
        when(reportRepositoryPort.save(extracted)).thenReturn(saved);

        FinancialMetrics result = service.extractAndSave("NVDA", 2025, "Annual");

        assertThat(result.revenue()).isEqualTo(130497.0);
        assertThat(result.netIncome()).isEqualTo(72880.0);
        verify(reportRepositoryPort).save(extracted);
    }

    @Test
    @DisplayName("extractAndSave: revenue is non-null after extraction")
    void revenueIsNonNull() {
        FinancialMetrics metricsWithRevenue = metrics("AAPL", 2024, "Q4", 94930.0, 14736.0);
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap())).thenReturn(List.of());
        when(metricsExtractionPort.extract(any(), anyInt(), any(), anyList())).thenReturn(metricsWithRevenue);
        when(reportRepositoryPort.save(any())).thenReturn(metricsWithRevenue);

        FinancialMetrics result = service.extractAndSave("AAPL", 2024, "Q4");

        assertThat(result.revenue()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("getGraph: company + report + metric nodes are built from stored data")
    void graphContainsCompanyReportAndMetricNodes() {
        FinancialMetrics m = metrics("NVDA", 2025, "Annual", 130497.0, 72880.0);
        when(reportRepositoryPort.findAllByTicker("NVDA")).thenReturn(List.of(m));

        MetricsGraph graph = service.getGraph("NVDA");

        assertThat(graph.nodes()).extracting("type")
                .contains("company", "report", "metric");
        assertThat(graph.edges()).extracting("label")
                .contains("HAS_REPORT", "HAS_METRIC");

        // Company node
        assertThat(graph.nodes()).anyMatch(n -> n.id().equals("NVDA") && n.type().equals("company"));
        // Report node
        assertThat(graph.nodes()).anyMatch(n -> n.id().equals("NVDA-2025-Annual") && n.type().equals("report"));
        // Metric nodes for non-null values (revenue + netIncome both set)
        assertThat(graph.nodes()).anyMatch(n -> n.id().contains("revenue") && n.type().equals("metric"));
    }

    @Test
    @DisplayName("getMetrics: delegates to repository")
    void getMetricsDelegatesToRepository() {
        FinancialMetrics m = metrics("NVDA", 2025, "Annual", 130497.0, null);
        when(reportRepositoryPort.findByTickerAndYearAndQuarter("NVDA", 2025, "Annual"))
                .thenReturn(Optional.of(m));

        Optional<FinancialMetrics> result = service.getMetrics("NVDA", 2025, "Annual");

        assertThat(result).isPresent();
        assertThat(result.get().ticker()).isEqualTo("NVDA");
    }

    // ---- helpers ----

    private static FinancialMetrics metrics(String ticker, int year, String quarter,
                                             Double revenue, Double netIncome) {
        return new FinancialMetrics(ticker, year, quarter, revenue, netIncome, null, null, null);
    }
}
