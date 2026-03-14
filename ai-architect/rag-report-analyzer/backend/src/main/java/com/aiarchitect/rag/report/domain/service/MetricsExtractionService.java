package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.graph.GraphEdge;
import com.aiarchitect.rag.report.domain.model.graph.GraphNode;
import com.aiarchitect.rag.report.domain.model.graph.MetricsGraph;
import com.aiarchitect.rag.report.domain.port.in.MetricsExtractionFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.MetricsExtractionPort;
import com.aiarchitect.rag.report.domain.port.out.ReportRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates structured metrics extraction from ingested report documents.
 *
 * <pre>
 * Phase 4: Structured Metrics Extraction
 *  1. Retrieve chunks scoped to ticker+year (financial-keyword query)
 *  2. Call MetricsExtractionPort — LLM returns structured FinancialMetrics
 *  3. Persist via ReportRepositoryPort
 *  4. Build D3 graph on demand from persisted data
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsExtractionService implements MetricsExtractionFacade {

    public static final int METRICS_TOP_K = 10;

    // Broad financial query to pull in chunks covering all key metrics
    public static final String METRICS_QUERY =
            "revenue net income earnings per share operating cash flow debt equity";

    private final DocumentStorePort documentStorePort;
    private final MetricsExtractionPort metricsExtractionPort;
    private final ReportRepositoryPort reportRepositoryPort;

    @Override
    public FinancialMetrics extractAndSave(String ticker, int year, String quarter) {
        log.info("Extracting metrics: ticker={}, year={}, quarter={}", ticker, year, quarter);

        Map<String, Object> filter = Map.of("ticker", ticker, "year", year);
        List<Document> chunks = documentStorePort.similaritySearch(METRICS_QUERY, METRICS_TOP_K, filter);

        log.debug("Retrieved {} chunks for metrics extraction", chunks.size());

        FinancialMetrics metrics = metricsExtractionPort.extract(ticker, year, quarter, chunks);
        FinancialMetrics saved = reportRepositoryPort.save(metrics);

        log.info("Saved metrics for {}/{}/{}: revenue={}, netIncome={}",
                ticker, year, quarter, saved.revenue(), saved.netIncome());
        return saved;
    }

    @Override
    public Optional<FinancialMetrics> getMetrics(String ticker, int year, String quarter) {
        return reportRepositoryPort.findByTickerAndYearAndQuarter(ticker, year, quarter);
    }

    @Override
    public MetricsGraph getGraph(String ticker) {
        List<FinancialMetrics> allMetrics = reportRepositoryPort.findAllByTicker(ticker);

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // Company node
        nodes.add(new GraphNode(ticker, "company", Map.of("name", ticker)));

        for (FinancialMetrics m : allMetrics) {
            String reportId = "%s-%d-%s".formatted(ticker, m.year(), m.quarter());

            // Report node
            nodes.add(new GraphNode(reportId, "report",
                    Map.of("year", m.year(), "quarter", m.quarter())));
            edges.add(new GraphEdge(ticker, reportId, "HAS_REPORT"));

            // Metric nodes — only for non-null values
            addMetricNode(nodes, edges, reportId, "revenue", m.revenue(), "M USD");
            addMetricNode(nodes, edges, reportId, "netIncome", m.netIncome(), "M USD");
            addMetricNode(nodes, edges, reportId, "eps", m.eps(), "USD/share");
            addMetricNode(nodes, edges, reportId, "operatingCashFlow", m.operatingCashFlow(), "M USD");
            addMetricNode(nodes, edges, reportId, "debtToEquity", m.debtToEquity(), "ratio");
        }

        return new MetricsGraph(nodes, edges);
    }

    private void addMetricNode(List<GraphNode> nodes, List<GraphEdge> edges,
                                String reportId, String name, Double value, String unit) {
        if (value == null) return;
        String metricId = "%s-%s".formatted(reportId, name);
        nodes.add(new GraphNode(metricId, "metric",
                Map.of("name", name, "value", value, "unit", unit)));
        edges.add(new GraphEdge(reportId, metricId, "HAS_METRIC"));
    }
}
