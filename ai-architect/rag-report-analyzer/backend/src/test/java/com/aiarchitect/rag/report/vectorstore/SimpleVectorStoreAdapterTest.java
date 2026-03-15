package com.aiarchitect.rag.report.vectorstore;

import com.aiarchitect.rag.report.infrastructure.adapter.out.vectorstore.SimpleVectorStoreAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SimpleVectorStoreAdapter}.
 *
 * <p>Uses a keyword-based mock {@link EmbeddingModel} so similarity search
 * returns meaningful results without a real AI provider:
 * <ul>
 *   <li>dim 0 → "income / revenue" signal
 *   <li>dim 1 → "cash / flow" signal
 *   <li>dim 2 → "debt / equity" signal
 * </ul>
 * Documents with a matching keyword get a 1.0 on the corresponding dimension;
 * {@link SimpleVectorStore} then ranks them by cosine similarity.
 */
@DisplayName("SimpleVectorStoreAdapter — similarity search")
class SimpleVectorStoreAdapterTest {

    private SimpleVectorStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Keyword-derived 3-dimensional embeddings (normalised by SimpleVectorStore).
        // SimpleVectorStore calls embed(Document) when storing; embed(String) when searching.
        // Both overloads must return non-null float[] or SimpleVectorStore throws.
        when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                keywordVector(inv.getArgument(0, String.class)));
        when(embeddingModel.embed(any(Document.class))).thenAnswer(inv ->
                keywordVector(inv.getArgument(0, Document.class).getText()));

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        adapter = new SimpleVectorStoreAdapter(vectorStore, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("store and retrieve: top-3 for 'operating income' are income/revenue docs")
    void topKResultsForIncomeQueryAreRelevant() {
        List<Document> docs = List.of(
                doc("NVDA Q1 operating income rose 120% driven by data-center revenue."),
                doc("Total revenue increased to $26B, net income reached $14.9B."),
                doc("Free cash flow from operations was $11.3B."),
                doc("Long-term debt stands at $9.7B; total equity is $42B."),
                doc("General corporate overhead and administrative expenses."),
                doc("Revenue growth accelerated in the second half of the fiscal year.")
        );

        adapter.store(docs);

        List<Document> results = adapter.similaritySearch("operating income and revenue growth", 3);

        assertThat(results).hasSize(3);
        // All top-3 results should mention income or revenue
        assertThat(results).allSatisfy(result ->
                assertThat(result.getText().toLowerCase())
                        .containsAnyOf("income", "revenue")
        );
    }

    @Test
    @DisplayName("store and retrieve: topK is respected")
    void topKIsRespected() {
        List<Document> docs = List.of(
                doc("Cash flow from operations."),
                doc("Free cash equivalents on hand."),
                doc("Operating cash position improved."),
                doc("Income tax provisions reduced earnings."),
                doc("Equity buyback program announced.")
        );

        adapter.store(docs);

        assertThat(adapter.similaritySearch("cash flow", 2)).hasSize(2);
        assertThat(adapter.similaritySearch("cash flow", 5)).hasSize(5);
    }

    @Test
    @DisplayName("store and retrieve: metadata is preserved")
    void metadataIsPreserved() {
        Document doc = new Document(
                "Total revenue for the quarter was $30B.",
                Map.of("ticker", "NVDA", "year", 2025, "quarter", "Q1")
        );

        adapter.store(List.of(doc));

        List<Document> results = adapter.similaritySearch("revenue", 1);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMetadata())
                .containsEntry("ticker", "NVDA")
                .containsEntry("year", 2025)
                .containsEntry("quarter", "Q1");
    }

    // ---- helpers ----

    private static Document doc(String text) {
        return new Document(text);
    }

    /**
     * Returns a 3-dim keyword-derived vector: [income/revenue, cash/flow, debt/equity].
     * Uses 0.01f as the minimum to avoid zero-norm vectors (SimpleVectorStore rejects them).
     */
    private static float[] keywordVector(String text) {
        String t = text.toLowerCase();
        return new float[]{
                contains(t, "income", "revenue") ? 1.0f : 0.01f,
                contains(t, "cash", "flow")      ? 1.0f : 0.01f,
                contains(t, "debt", "equity")    ? 1.0f : 0.01f
        };
    }

    private static boolean contains(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
