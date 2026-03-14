package com.aiarchitect.rag.report.domain.port.out;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Outbound port: abstracts the vector store.
 *
 * <p>Implementations may be backed by {@code SimpleVectorStore} (phases 1-4)
 * or ChromaDB (phase 5+). Domain code never depends on a concrete store.
 */
public interface DocumentStorePort {

    /**
     * Embeds and persists the given documents.
     */
    void store(List<Document> documents);

    /**
     * Returns the {@code topK} most similar documents for the given query.
     */
    List<Document> similaritySearch(String query, int topK);
}
