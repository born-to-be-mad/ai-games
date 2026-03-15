package com.aiarchitect.rag.report.resilience;

import com.aiarchitect.rag.report.domain.model.LlmCallException;
import com.aiarchitect.rag.report.domain.port.in.FinancialQaFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.domain.service.FinancialQaService;
import com.aiarchitect.rag.report.infrastructure.adapter.out.ai.SpringAiLanguageAdapter;
import com.aiarchitect.rag.report.infrastructure.config.ResilienceConfig;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies Spring Retry on {@link SpringAiLanguageAdapter#answer}.
 *
 * <p>When every LLM attempt throws, the retry interceptor must invoke the adapter
 * {@code maxAttempts=3} times before the {@code @Recover} method fires with
 * {@link LlmCallException}.
 *
 * <p>What I Learned:
 * <ul>
 *   <li>{@code @Retryable} is AOP-based — it only fires through a Spring proxy. The test
 *       calls through {@link FinancialQaFacade} → {@link LanguageModelPort} proxy chain.</li>
 *   <li>{@code @Recover} must be {@code public}, in the same class, and have a matching
 *       return type with the failed exception as the first parameter.</li>
 *   <li>Spring Retry auto-exports {@code spring.retry.*} metrics when Micrometer is on
 *       the classpath — no extra configuration needed.</li>
 * </ul>
 */
@SpringBootTest(classes = {
        FinancialQaService.class,
        SpringAiLanguageAdapter.class,
        ResilienceConfig.class,
        LlmRetryTest.TestConfig.class
})
@DisplayName("LLM retry behaviour")
class LlmRetryTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        AiProviderProperties aiProviderProperties() {
            return new AiProviderProperties("openai");
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @MockitoBean
    Map<String, ChatClient> chatClientsByProvider;

    @MockitoBean
    DocumentStorePort documentStorePort;

    // Inject by interface — @Retryable/@Cacheable proxies implement the interface
    @Autowired
    FinancialQaFacade qaFacade;

    @Autowired
    LanguageModelPort languageModelPort;

    @Test
    @DisplayName("Throws LlmCallException after 3 failed attempts")
    void retriesThreeTimesBeforeFailing() {
        when(documentStorePort.similaritySearch(anyString(), anyInt(), any())).thenReturn(List.of());
        // No ChatClient registered for provider → IllegalStateException on every attempt
        when(chatClientsByProvider.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> qaFacade.ask("What was revenue?", "NVDA", 2025))
                .isInstanceOf(LlmCallException.class)
                .hasMessageContaining("LLM unavailable after retries");

        // Adapter invoked 3 times (initial + 2 retries) before @Recover fired
        verify(chatClientsByProvider, times(3)).get("openai");
    }
}
