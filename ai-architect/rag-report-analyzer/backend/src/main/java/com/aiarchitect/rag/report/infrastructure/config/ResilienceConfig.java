package com.aiarchitect.rag.report.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.time.Duration;

/**
 * Enables Spring Retry (AOP-based) and Spring Cache (Caffeine) across the application.
 *
 * <h3>Retry</h3>
 * {@code @EnableRetry} activates the Spring Retry AOP proxy. Any bean method annotated
 * with {@code @Retryable} will be intercepted and retried on failure. See adapters:
 * <ul>
 *   <li>{@code SpringAiLanguageAdapter} — Q&A LLM calls</li>
 *   <li>{@code SpringAiMetricsExtractionAdapter} — structured extraction calls</li>
 *   <li>{@code LlmEvalJudgeAdapter} — evaluation judge calls</li>
 * </ul>
 *
 * <h3>Cache</h3>
 * {@code @EnableCaching} activates the Spring Cache AOP proxy. Caffeine is configured as
 * the backing store with a 30-minute TTL and 500-entry max. Cache names:
 * <ul>
 *   <li>{@code qa-answers} — keyed by {@code ticker:year:question}</li>
 * </ul>
 *
 * <p><b>Trade-off note:</b> {@code @Cacheable} is placed on {@code FinancialQaService}
 * (domain layer) for simplicity. In a stricter hexagonal setup, a caching decorator in
 * {@code infrastructure/adapter/in/} would keep the domain free of Spring annotations.
 */
@Configuration
@EnableRetry
@EnableCaching
public class ResilienceConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("qa-answers");
        manager.setCaffeine(Caffeine.newBuilder()
                .recordStats()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return manager;
    }
}
