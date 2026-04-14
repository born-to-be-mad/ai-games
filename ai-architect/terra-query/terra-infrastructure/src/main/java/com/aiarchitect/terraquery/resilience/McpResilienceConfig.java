package com.aiarchitect.terraquery.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.ConnectException;
import java.time.Duration;

/**
 * Resilience4j configuration for the MCP supervisor circuit breaker.
 *
 * Circuit breaker (mcp-supervisor):
 *   - Opens after 50% failures in a 10-call sliding window
 *   - Waits 30s before allowing probe calls (HALF_OPEN)
 *   - Metrics auto-published to Micrometer (Prometheus)
 *
 * Retry (mcp-supervisor):
 *   - 2 attempts max (1 retry) for transient network errors only
 *   - 1s wait between attempts
 *   - Does NOT retry TimeoutException (agent already timed out — no point)
 */
@Configuration
public class McpResilienceConfig {

    public static final String CB_NAME = "mcp-supervisor";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(90))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(ConnectException.class, java.io.IOException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // Pre-create the named instance so Micrometer can bind to it at startup
        registry.circuitBreaker(CB_NAME);

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofSeconds(1))
                .retryExceptions(ConnectException.class, java.io.IOException.class)
                .ignoreExceptions(
                        java.util.concurrent.TimeoutException.class,
                        IllegalArgumentException.class
                )
                .build();

        return RetryRegistry.of(config);
    }
}
