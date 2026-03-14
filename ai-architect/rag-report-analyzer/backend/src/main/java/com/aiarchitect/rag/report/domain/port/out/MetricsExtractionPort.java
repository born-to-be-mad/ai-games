package com.aiarchitect.rag.report.domain.port.out;

import com.aiarchitect.rag.report.domain.model.FinancialMetrics;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Outbound port: extracts structured {@link FinancialMetrics} from document chunks
 * using an LLM.
 *
 * <p>The adapter is responsible for prompt assembly, structured output parsing
 * (e.g. via {@code ChatClient.call().entity()}), and mapping to the domain record.
 */
public interface MetricsExtractionPort {

    /**
     * Extracts financial metrics from the provided context chunks.
     *
     * @param ticker  company ticker for metadata annotation
     * @param year    fiscal year for metadata annotation
     * @param quarter reporting period for metadata annotation
     * @param chunks  document chunks to use as extraction context
     * @return extracted metrics (fields may be null if not found in context)
     */
    FinancialMetrics extract(String ticker, int year, String quarter, List<Document> chunks);
}
