package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import com.aiarchitect.rag.report.domain.port.out.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter: wraps Spring AI's {@link EmbeddingModel}.
 *
 * <p>The active model is chosen by configuration:
 * <ul>
 *   <li>OpenAI — {@code text-embedding-3-small} (1536 dims)
 *   <li>Ollama — {@code nomic-embed-text} (768 dims)
 * </ul>
 *
 * <p>Keeping this adapter separate lets tests swap the embedding model via
 * {@code @MockitoBean} without touching domain code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    @Qualifier("activeEmbeddingModel")
    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        log.debug("Embedding text (length={})", text.length());
        float[] vector = embeddingModel.embed(text);
        log.debug("Produced vector of {} dimensions", vector.length);
        return vector;
    }
}
