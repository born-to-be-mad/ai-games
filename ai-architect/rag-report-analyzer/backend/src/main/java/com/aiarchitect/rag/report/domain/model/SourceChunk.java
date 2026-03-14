package com.aiarchitect.rag.report.domain.model;

/**
 * A retrieved document chunk that was used to compose the LLM answer.
 *
 * @param chunkId    unique ID of the chunk in the vector store
 * @param text       preview text (up to 300 chars)
 * @param pageNumber source page number from the original PDF, or "unknown"
 */
public record SourceChunk(String chunkId, String text, String pageNumber) {
}
