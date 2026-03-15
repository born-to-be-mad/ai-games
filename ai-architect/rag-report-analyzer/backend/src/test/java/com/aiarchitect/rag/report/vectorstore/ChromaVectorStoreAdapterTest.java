package com.aiarchitect.rag.report.vectorstore;

import com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore.ChromaVectorStoreAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link ChromaVectorStoreAdapter}.
 *
 * <p>Verifies that the filtered search uses a native {@code filterExpression}
 * (passed to the {@link VectorStore}) rather than fetching over-sized candidates
 * and post-filtering in memory — the key difference from {@link
 * com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore.SimpleVectorStoreAdapter}.
 */
@DisplayName("ChromaVectorStoreAdapter — native filter expressions")
class ChromaVectorStoreAdapterTest {

    private VectorStore vectorStore;
    private ChromaVectorStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        adapter = new ChromaVectorStoreAdapter(vectorStore, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("store: delegates add() to underlying VectorStore")
    void storeDelegatesAdd() {
        List<Document> docs = List.of(new Document("Revenue was $130B."));
        adapter.store(docs);
        verify(vectorStore).add(docs);
    }

    @Test
    @DisplayName("similaritySearch(query, topK): passes correct topK — no over-fetch")
    void unfiltered_passesExactTopK() {
        Document doc = new Document("Net income grew 120%.");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = adapter.similaritySearch("net income", 3);

        assertThat(results).hasSize(1);
        // Verify topK=3 is passed exactly — NOT topK*3 as SimpleVectorStoreAdapter does
        verify(vectorStore).similaritySearch(searchRequestMatching(req -> req.getTopK() == 3));
    }

    @Test
    @DisplayName("similaritySearch with filter: passes filterExpression to VectorStore (not null)")
    void filteredSearch_passesFilterExpressionToVectorStore() {
        Document doc = new Document("Revenue for NVDA was $130B.", Map.of("ticker", "NVDA", "year", 2025));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        adapter.similaritySearch("revenue", 5, Map.of("ticker", "NVDA", "year", 2025));

        // ChromaVectorStoreAdapter must pass a non-null filterExpression — filter pushed to DB
        verify(vectorStore).similaritySearch(searchRequestMatching(req ->
                req.getFilterExpression() != null));
    }

    @Test
    @DisplayName("similaritySearch with filter: topK is not multiplied (native filter, no over-fetch)")
    void filteredSearch_topKIsNotMultiplied() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.similaritySearch("revenue", 5, Map.of("ticker", "NVDA", "year", 2025));

        // topK should be exactly 5, not 15 (5*3 over-fetch as SimpleVectorStoreAdapter does)
        verify(vectorStore).similaritySearch(searchRequestMatching(req -> req.getTopK() == 5));
    }

    @Test
    @DisplayName("similaritySearch with empty filter: no filterExpression added")
    void filteredSearch_emptyFilter_noFilterExpression() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.similaritySearch("revenue", 5, Map.of());

        verify(vectorStore).similaritySearch(searchRequestMatching(req ->
                req.getFilterExpression() == null));
    }

    // ---- helpers ----

    /** Typed matcher to disambiguate similaritySearch(SearchRequest) from similaritySearch(String). */
    private static SearchRequest searchRequestMatching(ArgumentMatcher<SearchRequest> matcher) {
        return argThat(matcher);
    }
}
