package com.aiarchitect.rag.report.infrastructure.adapter.in.runner;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 1 demo runner: loads a sample PDF, chunks it, and prints the first 5 chunks.
 *
 * <p>Active only when {@code spring.profiles.active=demo} to avoid running on every startup.
 *
 * <pre>
 * What this demonstrates:
 *  - RAG ingestion step 1: document loading
 *  - RAG ingestion step 2: text chunking (TokenTextSplitter)
 *  - Metadata enrichment per chunk (ticker, year, quarter, page)
 *  - Hexagonal wiring: Runner → Facade → Service → Port → Adapter
 * </pre>
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

    private final ReportIngestionFacade reportIngestionFacade;

    @Value("${app.demo.pdf-path}")
    private String pdfPath;

    @Value("${app.demo.ticker:NVDA}")
    private String ticker;

    @Value("${app.demo.year:2025}")
    private int year;

    @Value("${app.demo.quarter:Annual}")
    private String quarter;

    @Override
    public void run(String... args) {
        log.info("=== Phase 1: PDF Ingestion Demo ===");
        log.info("Loading: {} | ticker={}, year={}, quarter={}", pdfPath, ticker, year, quarter);

        List<Document> chunks = reportIngestionFacade.ingest(pdfPath, ticker, year, quarter);

        log.info("Total chunks produced: {}", chunks.size());
        log.info("--- First 5 chunks ---");

        chunks.stream().limit(5).forEach(chunk -> {
            String preview = chunk.getText().length() > 200
                    ? chunk.getText().substring(0, 200) + "..."
                    : chunk.getText();

            log.info("""

                    [CHUNK]
                      text    : {}
                      metadata: {}
                    """,
                    preview,
                    chunk.getMetadata()
            );
        });

        log.info("=== Ingestion complete. {} total chunks ready for embedding. ===", chunks.size());
    }
}
