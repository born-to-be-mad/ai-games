package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.model.QaAnswer;
import com.aiarchitect.rag.report.domain.model.SourceChunk;
import com.aiarchitect.rag.report.domain.port.in.FinancialQaFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the RAG Q&A pipeline: retrieve → ground → generate.
 *
 * <pre>
 * Phase 3: Basic RAG Q&A
 *  1. similaritySearch with ticker+year metadata filter → top-k chunks
 *  2. Pass chunks as context to the LLM via LanguageModelPort
 *  3. Return answer + source references
 * </pre>
 *
 * <h3>Caching (Phase 10)</h3>
 * {@code @Cacheable("qa-answers")} caches responses by {@code ticker:year:question} key.
 * Backed by Caffeine with a 30-minute TTL and 500-entry max (configured in
 * {@code ResilienceConfig}). Identical questions for the same report never hit the LLM twice
 * within the TTL window.
 *
 * <p><b>Trade-off:</b> {@code @Cacheable} is a Spring Cache annotation placed in the domain
 * service for simplicity. In a stricter hexagonal setup, a caching decorator in the
 * infrastructure layer would keep the domain free of Spring annotations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialQaService implements FinancialQaFacade {

    public static final int DEFAULT_TOP_K = 5;

    private final DocumentStorePort documentStorePort;
    private final LanguageModelPort languageModelPort;

    @Cacheable(value = "qa-answers", key = "#ticker + ':' + #year + ':' + #question")
    @Override
    public QaAnswer ask(String question, String ticker, int year) {
        log.info("Q&A request: ticker={}, year={}, question='{}'", ticker, year, question);

        Map<String, Object> filter = Map.of("ticker", ticker, "year", year);
        List<Document> chunks = documentStorePort.similaritySearch(question, DEFAULT_TOP_K, filter);

        log.debug("Retrieved {} chunks for ticker={}, year={}", chunks.size(), ticker, year);

        String answer = languageModelPort.answer(question, chunks);

        List<SourceChunk> sources = chunks.stream()
                .map(doc -> new SourceChunk(
                        doc.getId(),
                        doc.getText().substring(0, Math.min(300, doc.getText().length())),
                        String.valueOf(doc.getMetadata().getOrDefault("page_number", "unknown"))))
                .toList();

        log.info("Answered question for ticker={}, year={} using {} source chunks", ticker, year, sources.size());
        return new QaAnswer(answer, sources);
    }
}
