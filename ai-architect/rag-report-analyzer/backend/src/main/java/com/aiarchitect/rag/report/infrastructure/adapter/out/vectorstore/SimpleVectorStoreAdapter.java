package com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore;

import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
