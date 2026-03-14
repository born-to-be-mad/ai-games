package com.aiarchitect.rag.report.domain.port.out;

/**
 * Outbound port: converts raw text into a dense embedding vector.
 *
 * <p>Kept deliberately narrow — callers receive a plain {@code float[]} so
 * the domain stays free of Spring AI types.  The concrete adapter delegates
 * to whatever {@code EmbeddingModel} is active (OpenAI / Ollama / mock).
 */
public interface EmbeddingPort {

    float[] embed(String text);
}
