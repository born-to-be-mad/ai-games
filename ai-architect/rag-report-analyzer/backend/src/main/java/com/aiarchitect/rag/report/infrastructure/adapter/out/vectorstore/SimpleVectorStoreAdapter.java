package com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore;

import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outbound adapter: delegates to Spring AI's {@link VectorStore}.
 *
 * <p>In phases 1-4 the backing store is {@code SimpleVectorStore} (in-memory).
 * Phase 5 swaps it for ChromaDB without touching this class or the domain —
 * only the {@code VectorStoreConfig} bean definition changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimpleVectorStoreAdapter implements DocumentStorePort {

    private final VectorStore vectorStore;

    @Override
    public void store(List<Document> documents) {
        log.debug("Storing {} documents in vector store", documents.size());
        vectorStore.add(documents);
        log.debug("Stored {} documents", documents.size());
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        log.debug("Similarity search: query='{}', topK={}", query, topK);
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
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
        List<Document> candidates = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK * 3).build());
        List<Document> results = candidates.stream()
                .filter(doc -> metadataFilter.entrySet().stream()
                        .allMatch(e -> e.getValue().equals(doc.getMetadata().get(e.getKey()))))
                .limit(topK)
                .toList();
        log.debug("Filtered to {} results for query='{}'", results.size(), query);
        return results;
    }
}
