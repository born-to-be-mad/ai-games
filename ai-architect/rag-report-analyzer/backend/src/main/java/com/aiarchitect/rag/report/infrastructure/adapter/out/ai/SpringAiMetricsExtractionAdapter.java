package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import com.aiarchitect.rag.report.domain.port.out.MetricsExtractionPort;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outbound adapter: extracts structured financial metrics via the LLM.
 *
 * <p>Uses {@code ChatClient.call().entity(FinancialMetricsDto.class)} which internally:
 * <ol>
 *   <li>Generates a JSON schema from {@link FinancialMetricsDto}
 *   <li>Appends format instructions to the prompt
 *   <li>Parses the LLM response into the DTO
 * </ol>
 * Maps the DTO to the domain {@link FinancialMetrics} record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiMetricsExtractionAdapter implements MetricsExtractionPort {

    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;

    @Value("classpath:prompts/metrics-extraction.st")
    private Resource systemPromptResource;

    @Override
    public FinancialMetrics extract(String ticker, int year, String quarter, List<Document> chunks) {
        String provider = aiProviderProperties.active();
        ChatClient client = chatClientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "LLM provider '%s' is not configured. Available: %s"
                            .formatted(provider, chatClientsByProvider.keySet()));
        }

        String context = chunks.stream()
                .map(doc -> "Source [page=%s]:\n%s".formatted(
                        doc.getMetadata().getOrDefault("page_number", "?"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("Extracting metrics for {}/{}/{} using {} chunks via {}",
                ticker, year, quarter, chunks.size(), provider);

        FinancialMetricsDto dto = client.prompt()
                .system(s -> s.text(systemPromptResource))
                .user(u -> u.text("""
                        Company: {ticker}
                        Fiscal year: {year}
                        Quarter: {quarter}

                        Report excerpts:
                        {context}
                        """)
                        .param("ticker", ticker)
                        .param("year", String.valueOf(year))
                        .param("quarter", quarter)
                        .param("context", context))
                .call()
                .entity(FinancialMetricsDto.class);

        log.debug("Extracted: revenue={}, netIncome={}, eps={}",
                dto.getRevenue(), dto.getNetIncome(), dto.getEps());

        return new FinancialMetrics(
                ticker, year, quarter,
                dto.getRevenue(),
                dto.getNetIncome(),
                dto.getEps(),
                dto.getOperatingCashFlow(),
                dto.getDebtToEquity());
    }
}
