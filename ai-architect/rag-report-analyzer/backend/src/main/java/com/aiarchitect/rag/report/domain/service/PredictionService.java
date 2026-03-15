package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.in.PredictionFacade;
import com.aiarchitect.rag.report.domain.port.out.PredictionStrategy;
import com.aiarchitect.rag.report.domain.port.out.ReportRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Domain service: loads historical metrics for the requested ticker and delegates
 * to the matching {@link PredictionStrategy}.
 *
 * <p>All registered {@code PredictionStrategy} beans are injected as a list and
 * keyed by their {@link PredictionStrategy#mode()} — no switch or if/else in application code.
 */
@Slf4j
@Service
public class PredictionService implements PredictionFacade {

    private final Map<PredictionMode, PredictionStrategy> strategies;
    private final ReportRepositoryPort repositoryPort;

    public PredictionService(List<PredictionStrategy> strategies, ReportRepositoryPort repositoryPort) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(PredictionStrategy::mode, s -> s));
        this.repositoryPort = repositoryPort;
    }

    @Override
    public FinancialOutlook predict(String ticker, PredictionMode mode) {
        List<FinancialMetrics> history = repositoryPort.findAllByTicker(ticker);
        if (history.isEmpty()) {
            throw new IllegalArgumentException(
                    "No metrics found for ticker '%s'. Ingest and extract metrics first.".formatted(ticker));
        }

        PredictionStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No strategy registered for mode '%s'. Available: %s".formatted(mode, strategies.keySet()));
        }

        log.info("Predicting {} for {} across {} historical periods using {}",
                mode, ticker, history.size(), strategy.getClass().getSimpleName());
        return strategy.predict(ticker, history);
    }
}
