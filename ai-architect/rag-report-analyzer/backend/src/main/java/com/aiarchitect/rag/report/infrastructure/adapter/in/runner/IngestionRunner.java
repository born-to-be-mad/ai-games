package com.aiarchitect.rag.report.infrastructure.adapter.in.runner;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demo runner for phases 1-2: ingests a PDF then runs similarity searches
 * to demonstrate the full embed → store → retrieve cycle.
 *
 * <p>Active only with {@code --spring.profiles.active=demo}.
 *
 * <pre>
 * Phase 1: read PDF → chunk → print first 5 chunks
 * Phase 2: embed chunks → store in SimpleVectorStore → similarity search
 * </pre>
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

    private static final int TOP_K = 3;

    private final ReportIngestionFacade reportIngestionFacade;
    private final DocumentStorePort documentStorePort;

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
        log.info("=== Phase 1+2 Demo: Ingest → Embed → Store → Search ===");
        log.info("PDF: {} | ticker={}, year={}, quarter={}", pdfPath, ticker, year, quarter);

        List<Document> chunks = reportIngestionFacade.ingest(pdfPath, ticker, year, quarter);
        log.info("Total chunks ingested and stored: {}", chunks.size());

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

        log.info("--- Similarity search demos ---");
        List<String> queries = List.of(
                "operating income and revenue growth",
                "research and development expenses",
                "risk factors and competition"
        );

        queries.forEach(query -> {
            List<Document> results = documentStorePort.similaritySearch(query, TOP_K);
            log.info("\nQuery: '{}'\nTop {} results:", query, TOP_K);
            results.forEach(doc -> {
                String preview = doc.getText().length() > 150
                        ? doc.getText().substring(0, 150) + "..."
                        : doc.getText();
                log.info("  [metadata={}]\n  {}", doc.getMetadata(), preview);
            });
        });

        log.info("=== Demo complete. {} chunks in vector store. ===", chunks.size());
    }
}
