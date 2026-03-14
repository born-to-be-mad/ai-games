package com.aiarchitect.rag.report.service;

import com.aiarchitect.rag.report.domain.model.QaAnswer;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.domain.service.FinancialQaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link FinancialQaService}.
 *
 * <p>Verifies the RAG orchestration logic: metadata filter is forwarded to the store,
 * the LLM is called with retrieved chunks, and the response includes sources.
 * No real LLM or vector store is used.
 */
@DisplayName("FinancialQaService — RAG orchestration")
class FinancialQaServiceTest {

    private DocumentStorePort documentStorePort;
    private LanguageModelPort languageModelPort;
    private FinancialQaService service;

    @BeforeEach
    void setUp() {
        documentStorePort = mock(DocumentStorePort.class);
        languageModelPort = mock(LanguageModelPort.class);
        service = new FinancialQaService(documentStorePort, languageModelPort);
    }

    @Test
    @DisplayName("ask: metadata filter includes ticker and year")
    void metadataFilterIncludesTickerAndYear() {
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap()))
                .thenReturn(List.of());
        when(languageModelPort.answer(anyString(), anyList()))
                .thenReturn("I don't have enough information.");

        service.ask("What was net income?", "AAPL", 2024);

        verify(documentStorePort).similaritySearch(
                eq("What was net income?"),
                eq(FinancialQaService.DEFAULT_TOP_K),
                eq(Map.of("ticker", "AAPL", "year", 2024)));
    }

    @Test
    @DisplayName("ask: LLM is called with retrieved chunks")
    void llmIsCalledWithRetrievedChunks() {
        Document chunk = new Document("Net income was $100B.", Map.of("ticker", "NVDA", "year", 2025));
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap()))
                .thenReturn(List.of(chunk));
        when(languageModelPort.answer(anyString(), anyList()))
                .thenReturn("Net income was $100B.");

        service.ask("What was net income?", "NVDA", 2025);

        verify(languageModelPort).answer(eq("What was net income?"), eq(List.of(chunk)));
    }

    @Test
    @DisplayName("ask: answer and sources are returned")
    void answerAndSourcesAreReturned() {
        Document chunk = new Document(
                "Revenue for fiscal 2025 was $130B.",
                Map.of("ticker", "NVDA", "year", 2025, "page_number", "42"));
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap()))
                .thenReturn(List.of(chunk));
        when(languageModelPort.answer(anyString(), anyList()))
                .thenReturn("Revenue was $130B (page 42).");

        QaAnswer result = service.ask("What was revenue?", "NVDA", 2025);

        assertThat(result.answer()).isEqualTo("Revenue was $130B (page 42).");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().pageNumber()).isEqualTo("42");
        assertThat(result.sources().getFirst().text()).contains("Revenue for fiscal 2025");
    }

    @Test
    @DisplayName("ask: empty store returns answer with no sources")
    void emptyStoreReturnsAnswerWithNoSources() {
        when(documentStorePort.similaritySearch(anyString(), anyInt(), anyMap()))
                .thenReturn(List.of());
        when(languageModelPort.answer(anyString(), anyList()))
                .thenReturn("I don't have enough information in the provided report excerpts.");

        QaAnswer result = service.ask("What was EPS?", "TSLA", 2023);

        assertThat(result.answer()).contains("don't have enough information");
        assertThat(result.sources()).isEmpty();
    }
}
