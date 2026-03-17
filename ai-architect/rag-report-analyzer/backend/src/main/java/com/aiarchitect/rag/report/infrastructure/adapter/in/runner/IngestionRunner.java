package com.aiarchitect.rag.report.infrastructure.adapter.in.runner;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.infrastructure.props.IngestionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional startup runner: ingests a PDF then runs similarity searches
 * to verify the full embed → store → retrieve cycle.
 *
 * <p>Activated by setting {@code app.ingestion.run-on-start=true}
 * (env: {@code INGEST_ON_START=true}). Disabled by default.
 *
 * <pre>
 * 1. read PDF → chunk → print first 5 chunks
 * 2. embed chunks → store in VectorStore → similarity search
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ingestion.run-on-start", havingValue = "true")
@EnableConfigurationProperties(IngestionProperties.class)
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

    private static final int TOP_K = 3;

    private final ReportIngestionFacade reportIngestionFacade;
    private final DocumentStorePort documentStorePort;
    private final IngestionProperties props;

    @Override
    public void run(String... args) {
        log.info("=== Startup Ingestion: Ingest → Embed → Store → Search ===");
        log.info("PDF: {} | ticker={}, year={}, quarter={}",
                props.pdfPath(), props.ticker(), props.year(), props.quarter());

        List<Document> chunks = reportIngestionFacade.ingest(
                props.pdfPath(), props.ticker(), props.year(), props.quarter());
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

        log.info("=== Startup ingestion complete. {} chunks in vector store. ===", chunks.size());
    }
}
