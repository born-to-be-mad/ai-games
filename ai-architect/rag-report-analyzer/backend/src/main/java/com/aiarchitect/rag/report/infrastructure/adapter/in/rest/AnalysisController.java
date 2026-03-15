package com.aiarchitect.rag.report.infrastructure.adapter.in.rest;

import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.in.PredictionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter: exposes financial prediction via {@code GET /api/v1/analysis/{ticker}}.
 *
 * <p>The {@code mode} parameter selects the prediction algorithm at runtime:
 * <ul>
 *   <li>{@code NARRATIVE_PREDICTION} (default) — LLM qualitative analysis
 *   <li>{@code LINEAR_REGRESSION} — deterministic numeric extrapolation
 *   <li>{@code HYBRID} — statistical baseline + LLM narrative
 * </ul>
 *
 * <p>Returns {@code 400 Bad Request} if no metrics are stored for the ticker.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final PredictionFacade predictionFacade;

    @GetMapping("/{ticker}")
    public ResponseEntity<FinancialOutlook> predict(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "NARRATIVE_PREDICTION") PredictionMode mode) {
        log.info("Prediction request: ticker={}, mode={}", ticker, mode);
        try {
            FinancialOutlook outlook = predictionFacade.predict(ticker.toUpperCase(), mode);
            return ResponseEntity.ok(outlook);
        } catch (IllegalArgumentException e) {
            log.warn("Prediction failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
