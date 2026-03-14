package com.aiarchitect.rag.report.domain.port.out;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

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

    /**
     * Returns the {@code topK} most similar documents whose metadata matches all entries in
     * {@code metadataFilter}. Implementations backed by SimpleVectorStore perform post-filtering;
     * ChromaDB (phase 5) will use native filter expressions for efficiency.
     *
     * @param metadataFilter key/value pairs that every returned document must satisfy
     */
    List<Document> similaritySearch(String query, int topK, Map<String, Object> metadataFilter);
}
