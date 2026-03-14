package com.aiarchitect.rag.report.domain.port.in;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Inbound port (use case): ingest a financial report PDF and return chunked documents.
 */
public interface ReportIngestionFacade {

    /**
     * Loads and chunks the report at the given path.
     *
     * @param filePath absolute or relative path to the PDF
     * @param ticker   company ticker (e.g. NVDA)
     * @param year     report year
     * @param quarter  report quarter (e.g. Q4) or "Annual"
     * @return list of chunked {@link Document}s with metadata attached
     */
    List<Document> ingest(String filePath, String ticker, int year, String quarter);
}
