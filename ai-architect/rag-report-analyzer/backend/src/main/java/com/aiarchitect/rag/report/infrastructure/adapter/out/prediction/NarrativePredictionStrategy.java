package com.aiarchitect.rag.report.infrastructure.adapter.out.prediction;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.out.PredictionStrategy;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Prediction strategy: asks the LLM to analyze historical metrics and produce a qualitative outlook.
 *
 * <p>Uses Spring AI's {@code ChatClient.call().entity(FinancialOutlookDto.class)} for structured output.
 * The LLM receives a formatted table of historical periods and returns JSON matching {@link FinancialOutlookDto}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NarrativePredictionStrategy implements PredictionStrategy {

    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;

    @Value("classpath:prompts/prediction-narrative.st")
    private Resource systemPromptResource;

    @Override
    public PredictionMode mode() {
        return PredictionMode.NARRATIVE_PREDICTION;
    }

    @Override
    public FinancialOutlook predict(String ticker, List<FinancialMetrics> historicalMetrics) {
        String provider = aiProviderProperties.active();
        ChatClient client = chatClientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "LLM provider '%s' is not configured. Available: %s"
                            .formatted(provider, chatClientsByProvider.keySet()));
        }

        String metricsTable = formatMetricsTable(historicalMetrics);
        log.debug("Narrative prediction for {} with {} periods via {}", ticker, historicalMetrics.size(), provider);

        FinancialOutlookDto dto = client.prompt()
                .system(s -> s.text(systemPromptResource))
                .user(u -> u.text("""
                        Company: {ticker}
                        Number of historical periods: {count}

                        Historical financial metrics (all monetary values in millions USD):
                        {metricsTable}
                        """)
                        .param("ticker", ticker)
                        .param("count", String.valueOf(historicalMetrics.size()))
                        .param("metricsTable", metricsTable))
                .call()
                .entity(FinancialOutlookDto.class);

        return new FinancialOutlook(
                dto.getTrend(),
                dto.getRisks(),
                dto.getPredictedRevenueRange(),
                dto.getConfidence(),
                "NARRATIVE_PREDICTION: LLM qualitative trend analysis");
    }

    static String formatMetricsTable(List<FinancialMetrics> metrics) {
        String header = "Period | Revenue($M) | NetIncome($M) | EPS | OpCashFlow($M) | Debt/Equity";
        String separator = "-------|-------------|---------------|-----|----------------|------------";
        String rows = IntStream.range(0, metrics.size())
                .mapToObj(i -> {
                    FinancialMetrics m = metrics.get(i);
                    return "%s %s/%s | %s | %s | %s | %s | %s".formatted(
                            i + 1,
                            m.year(), m.quarter(),
                            formatNullable(m.revenue()),
                            formatNullable(m.netIncome()),
                            formatNullable(m.eps()),
                            formatNullable(m.operatingCashFlow()),
                            formatNullable(m.debtToEquity()));
                })
                .collect(Collectors.joining("\n"));
        return header + "\n" + separator + "\n" + rows;
    }

    private static String formatNullable(Double value) {
        return value != null ? "%.2f".formatted(value) : "N/A";
    }
}
