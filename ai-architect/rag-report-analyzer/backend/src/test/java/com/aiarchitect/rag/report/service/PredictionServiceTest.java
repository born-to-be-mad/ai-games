package com.aiarchitect.rag.report.service;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.out.PredictionStrategy;
import com.aiarchitect.rag.report.domain.port.out.ReportRepositoryPort;
import com.aiarchitect.rag.report.domain.service.PredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PredictionService — strategy dispatch")
class PredictionServiceTest {

    private ReportRepositoryPort repositoryPort;
    private PredictionStrategy narrativeStrategy;
    private PredictionStrategy lrStrategy;
    private PredictionService service;

    private final FinancialMetrics sampleMetrics = new FinancialMetrics(
            "NVDA", 2024, "Q4", 22100.0, 12285.0, 4.93, 11499.0, 0.39);

    private final FinancialOutlook sampleOutlook = new FinancialOutlook(
            "Steady revenue growth", List.of("Competition"), "$25B–$28B", 0.75, "NARRATIVE_PREDICTION");

    @BeforeEach
    void setUp() {
        repositoryPort = mock(ReportRepositoryPort.class);

        narrativeStrategy = mock(PredictionStrategy.class);
        when(narrativeStrategy.mode()).thenReturn(PredictionMode.NARRATIVE_PREDICTION);

        lrStrategy = mock(PredictionStrategy.class);
        when(lrStrategy.mode()).thenReturn(PredictionMode.LINEAR_REGRESSION);

        service = new PredictionService(List.of(narrativeStrategy, lrStrategy), repositoryPort);
    }

    @Test
    @DisplayName("dispatches to NARRATIVE_PREDICTION strategy when mode matches")
    void dispatchesToNarrativeStrategy() {
        when(repositoryPort.findAllByTicker("NVDA")).thenReturn(List.of(sampleMetrics));
        when(narrativeStrategy.predict(eq("NVDA"), any())).thenReturn(sampleOutlook);

        FinancialOutlook result = service.predict("NVDA", PredictionMode.NARRATIVE_PREDICTION);

        assertThat(result).isEqualTo(sampleOutlook);
        verify(narrativeStrategy).predict(eq("NVDA"), eq(List.of(sampleMetrics)));
        verify(lrStrategy, never()).predict(any(), any());
    }

    @Test
    @DisplayName("dispatches to LINEAR_REGRESSION strategy when mode matches")
    void dispatchesToLinearRegressionStrategy() {
        FinancialOutlook lrOutlook = new FinancialOutlook(
                "Upward trend", List.of("Data noise"), "$25B–$27B", 0.6, "LINEAR_REGRESSION");
        when(repositoryPort.findAllByTicker("NVDA")).thenReturn(List.of(sampleMetrics));
        when(lrStrategy.predict(eq("NVDA"), any())).thenReturn(lrOutlook);

        FinancialOutlook result = service.predict("NVDA", PredictionMode.LINEAR_REGRESSION);

        assertThat(result.methodology()).contains("LINEAR_REGRESSION");
        verify(lrStrategy).predict(eq("NVDA"), eq(List.of(sampleMetrics)));
        verify(narrativeStrategy, never()).predict(any(), any());
    }

    @Test
    @DisplayName("throws IllegalArgumentException when no metrics stored for ticker")
    void throwsWhenNoMetrics() {
        when(repositoryPort.findAllByTicker("UNKNOWN")).thenReturn(List.of());

        assertThatThrownBy(() -> service.predict("UNKNOWN", PredictionMode.NARRATIVE_PREDICTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No metrics found for ticker");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unregistered mode (HYBRID not registered)")
    void throwsForUnregisteredMode() {
        when(repositoryPort.findAllByTicker("NVDA")).thenReturn(List.of(sampleMetrics));

        // Only NARRATIVE and LR are registered — HYBRID is not
        assertThatThrownBy(() -> service.predict("NVDA", PredictionMode.HYBRID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No strategy registered for mode");
    }

    @Test
    @DisplayName("passes full history list to strategy")
    void passesFullHistoryToStrategy() {
        List<FinancialMetrics> history = List.of(
                new FinancialMetrics("NVDA", 2023, "Q4", 18120.0, 9243.0, 3.71, 8347.0, 0.41),
                new FinancialMetrics("NVDA", 2024, "Q2", 30040.0, 16599.0, 6.57, 14489.0, 0.37),
                sampleMetrics);
        when(repositoryPort.findAllByTicker("NVDA")).thenReturn(history);
        when(narrativeStrategy.predict(any(), any())).thenReturn(sampleOutlook);

        service.predict("NVDA", PredictionMode.NARRATIVE_PREDICTION);

        verify(narrativeStrategy).predict("NVDA", history);
    }
}
