package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
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

    @Value("classpath:prompts/qa-system.st")
    private Resource systemPromptResource;

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

        return client.prompt()
                .system(s -> s.text(systemPromptResource))
                .user(u -> u.text("""
                        Context:
                        {context}

                        Question: {question}
                        """)
                        .param("context", context)
                        .param("question", question))
                .call()
                .content();
    }
}
