package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentReaderPort;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the RAG ingestion pipeline: read → chunk → enrich → embed → store.
 *
 * <p>Phase 1 covered read + chunk. Phase 2 adds embed + store via {@link DocumentStorePort}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportIngestionService implements ReportIngestionFacade {

    private final DocumentReaderPort documentReaderPort;
    private final DocumentStorePort documentStorePort;

    @Override
    public List<Document> ingest(String filePath, String ticker, int year, String quarter) {
        log.info("Ingesting report: ticker={}, year={}, quarter={}, file={}", ticker, year, quarter, filePath);

        List<Document> chunks = documentReaderPort.readAndChunk(filePath);

        Map<String, Object> reportMetadata = Map.of(
                "ticker", ticker,
                "year", year,
                "quarter", quarter,
                "report_type", "annual_report",
                "source_file", filePath
        );
        chunks.forEach(doc -> doc.getMetadata().putAll(reportMetadata));

        documentStorePort.store(chunks);

        log.info("Ingested and stored {} chunks for {}/{}", chunks.size(), ticker, year);
        return chunks;
    }
}
