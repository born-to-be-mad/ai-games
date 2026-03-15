package com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore;

import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outbound adapter: delegates to Spring AI's {@link org.springframework.ai.vectorstore.SimpleVectorStore}.
 *
 * <p>Active only when {@code app.vectorstore.type=simple} (the default).
 * Phase 5 adds {@link ChromaVectorStoreAdapter} for {@code type=chroma} which uses
 * native ChromaDB filter expressions instead of this post-filtering approach.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "simple", matchIfMissing = true)
public class SimpleVectorStoreAdapter implements DocumentStorePort {

    private final VectorStore vectorStore;
    private final MeterRegistry meterRegistry;

    @Override
    public void store(List<Document> documents) {
        log.debug("Storing {} documents in vector store", documents.size());
        Timer.builder("vector.store.operation")
                .tag("type", "simple")
                .tag("operation", "store")
                .register(meterRegistry)
                .record(() -> vectorStore.add(documents));
        log.debug("Stored {} documents", documents.size());
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        log.debug("Similarity search: query='{}', topK={}", query, topK);
        List<Document> results = Timer.builder("vector.store.operation")
                .tag("type", "simple")
                .tag("operation", "search")
                .register(meterRegistry)
                .record(() -> vectorStore.similaritySearch(
                        SearchRequest.builder().query(query).topK(topK).build()));
        log.debug("Found {} results for query='{}'", results.size(), query);
        return results;
    }

    /**
     * Fetches {@code topK * 3} candidates then post-filters by metadata.
     *
     * <p>SimpleVectorStore has no native filter support; ChromaDB (phase 5) will
     * replace this with a proper filter expression, keeping the domain unchanged.
     */
    @Override
    public List<Document> similaritySearch(String query, int topK, Map<String, Object> metadataFilter) {
        log.debug("Filtered search: query='{}', topK={}, filter={}", query, topK, metadataFilter);
        List<Document> results = Timer.builder("vector.store.operation")
                .tag("type", "simple")
                .tag("operation", "search_filtered")
                .register(meterRegistry)
                .record(() -> {
                    List<Document> candidates = vectorStore.similaritySearch(
                            SearchRequest.builder().query(query).topK(topK * 3).build());
                    return candidates.stream()
                            .filter(doc -> metadataFilter.entrySet().stream()
                                    .allMatch(e -> e.getValue().equals(doc.getMetadata().get(e.getKey()))))
                            .limit(topK)
                            .toList();
                });
        log.debug("Filtered to {} results for query='{}'", results.size(), query);
        return results;
    }
}
