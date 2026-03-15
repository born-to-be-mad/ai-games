package com.aiarchitect.rag.report.resilience;

import com.aiarchitect.rag.report.domain.model.QaAnswer;
import com.aiarchitect.rag.report.domain.port.in.FinancialQaFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.domain.service.FinancialQaService;
import com.aiarchitect.rag.report.infrastructure.config.ResilienceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies Caffeine cache on {@link FinancialQaService#ask}.
 *
 * <p>Two identical calls must invoke {@link LanguageModelPort#answer} only once —
 * the second call is served from the {@code qa-answers} cache.
 *
 * <p>What I Learned:
 * <ul>
 *   <li>{@code @Cacheable} is AOP-based — it only works when the method is called through
 *       a Spring proxy, not directly on {@code this}. Spring creates a JDK dynamic proxy
 *       implementing the {@link FinancialQaFacade} interface; therefore tests must inject
 *       by interface type, not the concrete {@link FinancialQaService} class.</li>
 *   <li>The cache key is evaluated via SpEL: {@code #ticker + ':' + #year + ':' + #question}.</li>
 *   <li>Tests share a Spring context — evict the cache in {@code @BeforeEach} to prevent
 *       stale entries from bleeding between test methods.</li>
 *   <li>Caffeine's {@code expireAfterWrite} and {@code maximumSize} are set in
 *       {@link ResilienceConfig}.</li>
 * </ul>
 */
@SpringBootTest(classes = {FinancialQaService.class, ResilienceConfig.class})
@DisplayName("Q&A response cache")
class QaResponseCacheTest {

    @MockitoBean
    DocumentStorePort documentStorePort;

    @MockitoBean
    LanguageModelPort languageModelPort;

    // Inject by interface — @Cacheable proxy implements FinancialQaFacade, not the concrete class
    @Autowired
    FinancialQaFacade qaFacade;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("qa-answers").clear();
    }

    @Test
    @DisplayName("Second identical call is served from cache — LLM called only once")
    void secondCallHitsCache() {
        when(documentStorePort.similaritySearch(anyString(), anyInt(), any())).thenReturn(List.of());
        when(languageModelPort.answer(anyString(), any())).thenReturn("Revenue was $130B.");

        QaAnswer first  = qaFacade.ask("What was revenue?", "NVDA", 2025);
        QaAnswer second = qaFacade.ask("What was revenue?", "NVDA", 2025);

        assertThat(first.answer()).isEqualTo(second.answer());
        verify(languageModelPort, times(1)).answer(anyString(), any());
    }

    @Test
    @DisplayName("Different ticker produces a cache miss — LLM called for each")
    void differentTickerBypassesCache() {
        when(documentStorePort.similaritySearch(anyString(), anyInt(), any())).thenReturn(List.of());
        when(languageModelPort.answer(anyString(), any()))
                .thenReturn("NVDA answer")
                .thenReturn("AAPL answer");

        QaAnswer nvda = qaFacade.ask("What was revenue?", "NVDA", 2025);
        QaAnswer aapl = qaFacade.ask("What was revenue?", "AAPL", 2025);

        assertThat(nvda.answer()).isEqualTo("NVDA answer");
        assertThat(aapl.answer()).isEqualTo("AAPL answer");
        verify(languageModelPort, times(2)).answer(anyString(), any());
    }
}
