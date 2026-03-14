package com.aiarchitect.rag.report.domain.port.out;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Outbound port: generates a grounded answer from the LLM given a question and
 * retrieved context chunks.
 *
 * <p>The concrete adapter is responsible for prompt assembly (system prompt template,
 * context formatting) and LLM invocation.
 */
public interface LanguageModelPort {

    /**
     * Generates a grounded answer using the provided context documents.
     *
     * @param question      the user's financial question
     * @param contextChunks retrieved chunks to serve as grounding context
     * @return the LLM-generated answer string
     */
    String answer(String question, List<Document> contextChunks);
}
