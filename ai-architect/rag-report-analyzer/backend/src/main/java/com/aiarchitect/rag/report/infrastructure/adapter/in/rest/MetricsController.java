package com.aiarchitect.rag.report.infrastructure.adapter.in.rest;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.graph.MetricsGraph;
import com.aiarchitect.rag.report.domain.port.in.MetricsExtractionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound adapter: exposes structured metrics extraction and retrieval over HTTP.
 *
 * <pre>
 * POST /api/v1/metrics/extract?ticker=NVDA&year=2025&quarter=Annual
 *   → triggers LLM extraction from ingested chunks, persists result
 *
 * GET /api/v1/metrics/{ticker}/{year}/{quarter}
 *   → returns previously extracted metrics (404 if not yet extracted)
 *
 * GET /api/v1/metrics/graph/{ticker}
 *   → returns D3-compatible knowledge graph: Company → Report → Metric
 * </pre>
 *
 * <p>Requires documents to be ingested first (Phase 1+2 pipeline).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsExtractionFacade metricsExtractionFacade;

    @PostMapping("/extract")
    public FinancialMetrics extract(
            @RequestParam String ticker,
            @RequestParam int year,
            @RequestParam String quarter) {
        log.info("POST /api/v1/metrics/extract — ticker={}, year={}, quarter={}", ticker, year, quarter);
        return metricsExtractionFacade.extractAndSave(ticker, year, quarter);
    }

    @GetMapping("/{ticker}/{year}/{quarter}")
    public ResponseEntity<FinancialMetrics> getMetrics(
            @PathVariable String ticker,
            @PathVariable int year,
            @PathVariable String quarter) {
        log.info("GET /api/v1/metrics/{}/{}/{}", ticker, year, quarter);
        return metricsExtractionFacade.getMetrics(ticker, year, quarter)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/graph/{ticker}")
    public MetricsGraph getGraph(@PathVariable String ticker) {
        log.info("GET /api/v1/metrics/graph/{}", ticker);
        return metricsExtractionFacade.getGraph(ticker);
    }
}
