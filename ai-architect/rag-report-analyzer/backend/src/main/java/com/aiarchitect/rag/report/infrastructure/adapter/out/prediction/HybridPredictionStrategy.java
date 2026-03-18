package com.aiarchitect.rag.report.infrastructure.adapter.out.prediction;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.model.FinancialOutlook;
import com.aiarchitect.rag.report.domain.model.PredictionMode;
import com.aiarchitect.rag.report.domain.port.out.PredictionStrategy;
import com.aiarchitect.rag.report.infrastructure.adapter.out.ai.LlmTokenMetrics;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Prediction strategy: runs linear regression for numeric ranges, then wraps the
 * statistical output in an LLM-generated narrative.
 *
 * <p>The LR result (predicted revenue range + slope) is passed to the LLM as additional context
 * so the narrative is grounded in the numbers rather than invented.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridPredictionStrategy implements PredictionStrategy {

    private final LinearRegressionStrategy linearRegressionStrategy;
    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;
    private final MeterRegistry meterRegistry;

    @Value("classpath:prompts/prediction-hybrid.st")
    private Resource systemPromptResource;

    @Override
    public PredictionMode mode() {
        return PredictionMode.HYBRID;
    }

    @Override
    public FinancialOutlook predict(String ticker, List<FinancialMetrics> historicalMetrics) {
        // Step 1: statistical baseline
        FinancialOutlook lrOutlook = linearRegressionStrategy.predict(ticker, historicalMetrics);

        String provider = aiProviderProperties.active();
        ChatClient client = chatClientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "LLM provider '%s' is not configured. Available: %s"
                            .formatted(provider, chatClientsByProvider.keySet()));
        }

        // Step 2: LLM enriches the statistical result with narrative
        String metricsTable = NarrativePredictionStrategy.formatMetricsTable(historicalMetrics);
        log.debug("Hybrid prediction for {} — LR baseline={}, calling {} for narrative",
                ticker, lrOutlook.predictedRevenueRange(), provider);

        var response = Timer.builder("llm.call.duration")
                .tag("provider", provider)
                .tag("operation", "prediction_hybrid")
                .register(meterRegistry)
                .record(() -> client.prompt()
                        .system(s -> s.text(systemPromptResource))
                        .user(u -> u.text("""
                                Company: {ticker}
                                Number of historical periods: {count}

                                Historical financial metrics (all monetary values in millions USD):
                                {metricsTable}

                                Linear regression results:
                                - Statistical trend: {lrTrend}
                                - Predicted revenue range (±10%%): {lrRange}
                                - Regression confidence (R²-based): {lrConfidence}
                                """)
                                .param("ticker", ticker)
                                .param("count", String.valueOf(historicalMetrics.size()))
                                .param("metricsTable", metricsTable)
                                .param("lrTrend", lrOutlook.trend())
                                .param("lrRange", lrOutlook.predictedRevenueRange())
                                .param("lrConfidence", "%.2f".formatted(lrOutlook.confidence())))
                        .call()
                        .responseEntity(FinancialOutlookDto.class));

        ChatResponse chatResponse = response != null ? response.response() : null;
        LlmTokenMetrics.record(meterRegistry, provider, "prediction_hybrid", chatResponse);
        FinancialOutlookDto dto = response != null ? response.entity() : null;
        if (dto == null) {
            throw new IllegalStateException("LLM returned empty hybrid prediction response");
        }

        return new FinancialOutlook(
                dto.getTrend(),
                dto.getRisks(),
                dto.getPredictedRevenueRange() != null ? dto.getPredictedRevenueRange() : lrOutlook.predictedRevenueRange(),
                dto.getConfidence(),
                "HYBRID: Linear regression (R²-based range) + LLM narrative");
    }
}
