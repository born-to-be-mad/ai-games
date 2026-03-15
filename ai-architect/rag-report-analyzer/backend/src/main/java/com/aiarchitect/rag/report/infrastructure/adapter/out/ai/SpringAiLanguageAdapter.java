package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import com.aiarchitect.rag.report.domain.model.LlmCallException;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outbound adapter: assembles the RAG prompt and calls the active LLM via Spring AI.
 *
 * <p>System prompt is loaded from {@code classpath:prompts/qa-system.st}.
 * The active provider is selected by {@code ai.provider.active} (defaults to {@code openai}).
 *
 * <h3>Resilience</h3>
 * <ul>
 *   <li>{@code @Retryable} — retries up to 3 times with exponential backoff (1s → 2s → 4s)
 *       on any exception (network timeout, rate-limit, transient API error).</li>
 *   <li>{@code @Recover} — after all retries are exhausted, throws {@link LlmCallException}.</li>
 * </ul>
 *
 * <h3>Observability</h3>
 * Records {@code llm.call.duration} timer with tags {@code provider} and {@code operation=qa}.
 *
 * <p>Context format passed to the LLM:
 * <pre>
 * Source [page=12]: Revenue for fiscal 2025 was $130B...
 * ---
 * Source [page=15]: Operating income increased by...
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiLanguageAdapter implements LanguageModelPort {

    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;
    private final MeterRegistry meterRegistry;

    @Value("classpath:prompts/qa-system.st")
    private Resource systemPromptResource;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Override
    public String answer(String question, List<Document> contextChunks) {
        String provider = aiProviderProperties.active();
        ChatClient client = chatClientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "LLM provider '%s' is not configured. Available: %s"
                            .formatted(provider, chatClientsByProvider.keySet()));
        }

        String context = contextChunks.stream()
                .map(doc -> "Source [page=%s]:\n%s".formatted(
                        doc.getMetadata().getOrDefault("page_number", "?"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("Calling {} with {} context chunks for question='{}'",
                provider, contextChunks.size(), question);

        return Timer.builder("llm.call.duration")
                .tag("provider", provider)
                .tag("operation", "qa")
                .register(meterRegistry)
                .record(() -> client.prompt()
                        .system(s -> s.text(systemPromptResource))
                        .user(u -> u.text("""
                                Context:
                                {context}

                                Question: {question}
                                """)
                                .param("context", context)
                                .param("question", question))
                        .call()
                        .content());
    }

    @Recover
    public String answerFallback(Exception ex, String question, List<Document> contextChunks) {
        log.error("LLM call failed after 3 retries for question='{}'", question, ex);
        throw new LlmCallException("LLM unavailable after retries: " + ex.getMessage(), ex);
    }
}
