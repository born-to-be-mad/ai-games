package com.aiarchitect.rag.report.infrastructure.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the active {@link VectorStore} bean.
 *
 * <p>Switch between implementations via {@code app.vectorstore.type}:
 * <ul>
 *   <li>{@code simple} (default) — in-memory {@link SimpleVectorStore}, zero setup
 *   <li>{@code chroma} — ChromaDB (phase 5), requires Docker service
 * </ul>
 *
 * <p>{@code @ConditionalOnProperty} ensures only one bean is created, so
 * {@link com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore.SimpleVectorStoreAdapter}
 * always gets a single {@link VectorStore} regardless of the chosen backend.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "simple", matchIfMissing = true)
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
