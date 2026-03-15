package com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore;

import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outbound adapter: delegates to ChromaDB via Spring AI's {@link VectorStore}.
 *
 * <p>Active only when {@code app.vectorstore.type=chroma}.
 *
 * <p>Key difference from {@link SimpleVectorStoreAdapter}: the metadata-filtered
 * {@code similaritySearch} uses ChromaDB's native {@code where} filter via
 * {@link FilterExpressionBuilder} instead of post-filtering in memory. This means:
 * <ul>
 *   <li>Chroma evaluates the filter before computing similarity — much more efficient
 *       at scale (no need to over-fetch with topK×3)
 *   <li>The filter is pushed to the database engine, reducing network transfer
 * </ul>
 *
 * <p>The domain code ({@link com.aiarchitect.rag.report.domain.port.out.DocumentStorePort})
 * is unchanged — this is the hexagonal architecture pay-off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "chroma")
public class ChromaVectorStoreAdapter implements DocumentStorePort {

    private final VectorStore vectorStore;

    @Override
    public void store(List<Document> documents) {
        log.debug("Storing {} documents in ChromaDB", documents.size());
        vectorStore.add(documents);
        log.debug("Stored {} documents", documents.size());
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        log.debug("ChromaDB similarity search: query='{}', topK={}", query, topK);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        log.debug("Found {} results", results.size());
        return results;
    }

    /**
     * Uses ChromaDB native {@code where} filter — evaluates the filter in the database,
     * not in application memory. No over-fetching needed.
     *
     * <p>Builds an AND expression from all entries in {@code metadataFilter}:
     * {@code {ticker: "NVDA", year: 2025} → ticker == "NVDA" AND year == 2025}
     */
    @Override
    public List<Document> similaritySearch(String query, int topK, Map<String, Object> metadataFilter) {
        log.debug("ChromaDB filtered search: query='{}', topK={}, filter={}", query, topK, metadataFilter);

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filterExpression = metadataFilter.entrySet().stream()
                .map(e -> b.eq(e.getKey(), e.getValue()))
                .reduce(b::and)
                .map(FilterExpressionBuilder.Op::build)
                .orElse(null);

        SearchRequest request = filterExpression != null
                ? SearchRequest.builder().query(query).topK(topK).filterExpression(filterExpression).build()
                : SearchRequest.builder().query(query).topK(topK).build();

        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("ChromaDB filtered to {} results for query='{}'", results.size(), query);
        return results;
    }
}
