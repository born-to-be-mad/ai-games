package com.aiarchitect.rag.report.infrastructure.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides the active {@link VectorStore} bean.
 *
 * <p>Switch between implementations via {@code app.vectorstore.type} (env: {@code VECTORSTORE_TYPE}):
 * <ul>
 *   <li>{@code simple} (default) — in-memory {@link SimpleVectorStore}, zero setup, zero persistence
 *   <li>{@code chroma} — {@link ChromaVectorStore} backed by a ChromaDB instance (Docker Compose)
 * </ul>
 *
 * <p>{@code @ConditionalOnProperty} ensures exactly one {@link VectorStore} bean exists.
 * The active {@link com.aiarchitect.rag.report.domain.port.out.DocumentStorePort} adapter
 * is selected by the same property on each adapter class.
 *
 * <p>ChromaDB is intentionally created without Spring AI auto-configuration to avoid
 * startup failures when {@code VECTORSTORE_TYPE=simple}.
 */
@Configuration
public class VectorStoreConfig {

    private static final String CHROMA_TENANT = "default_tenant";
    private static final String CHROMA_DATABASE = "default_database";
    private static final String CHROMA_COLLECTION = "rag-reports";

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "simple", matchIfMissing = true)
    public VectorStore simpleVectorStore(@Qualifier("activeEmbeddingModel") EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "chroma")
    public ChromaApi chromaApi(
            RestClient.Builder restClientBuilder,
            @Value("${app.chroma.host:localhost}") String host,
            @Value("${app.chroma.port:8000}") int port) {
        return ChromaApi.builder()
                .baseUrl("http://%s:%d".formatted(host, port))
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore.type", havingValue = "chroma")
    public VectorStore chromaVectorStore(
            ChromaApi chromaApi,
            @Qualifier("activeEmbeddingModel") EmbeddingModel embeddingModel) {

        ensureChromaSchema(chromaApi);

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .tenantName(CHROMA_TENANT)
                .databaseName(CHROMA_DATABASE)
                .collectionName(CHROMA_COLLECTION)
                .initializeSchema(false)
                .initializeImmediately(false)
                .build();
    }

    /**
     * Chroma 0.6.x can start before its tenant/database/collection metadata is fully ready.
     * Ensure required schema exists explicitly so Spring context startup doesn't fail.
     */
    private void ensureChromaSchema(ChromaApi chromaApi) {
        try {
            try {
                chromaApi.getTenant(CHROMA_TENANT);
            } catch (Exception ignored) {
                chromaApi.createTenant(CHROMA_TENANT);
            }

            try {
                chromaApi.getDatabase(CHROMA_TENANT, CHROMA_DATABASE);
            } catch (Exception ignored) {
                chromaApi.createDatabase(CHROMA_TENANT, CHROMA_DATABASE);
            }

            try {
                chromaApi.getCollection(CHROMA_TENANT, CHROMA_DATABASE, CHROMA_COLLECTION);
            } catch (Exception ignored) {
                chromaApi.createCollection(
                        CHROMA_TENANT,
                        CHROMA_DATABASE,
                        new ChromaApi.CreateCollectionRequest(CHROMA_COLLECTION));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Chroma schema", e);
        }
    }
}
