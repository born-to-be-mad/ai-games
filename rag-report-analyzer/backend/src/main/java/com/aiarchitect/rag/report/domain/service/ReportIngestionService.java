package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentReaderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportIngestionService implements ReportIngestionFacade {

    private final DocumentReaderPort documentReaderPort;

    @Override
    public List<Document> ingest(String filePath, String ticker, int year, String quarter) {
        log.info("Ingesting report: ticker={}, year={}, quarter={}, file={}", ticker, year, quarter, filePath);

        List<Document> chunks = documentReaderPort.readAndChunk(filePath);

        // Enrich each chunk with financial report metadata
        Map<String, Object> reportMetadata = Map.of(
                "ticker", ticker,
                "year", year,
                "quarter", quarter,
                "report_type", "annual_report",
                "source_file", filePath
        );

        chunks.forEach(doc -> doc.getMetadata().putAll(reportMetadata));

        log.info("Ingested {} chunks from {}", chunks.size(), filePath);
        return chunks;
    }
}
