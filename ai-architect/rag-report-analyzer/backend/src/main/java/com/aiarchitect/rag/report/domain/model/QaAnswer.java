package com.aiarchitect.rag.report.domain.model;

import java.util.List;

/**
 * The answer to a financial Q&A query, including the LLM-generated response
 * and the source chunks retrieved from the vector store.
 *
 * @param answer  LLM-generated answer grounded in the retrieved context
 * @param sources list of chunks used as context for the answer
 */
public record QaAnswer(String answer, List<SourceChunk> sources) {
}
