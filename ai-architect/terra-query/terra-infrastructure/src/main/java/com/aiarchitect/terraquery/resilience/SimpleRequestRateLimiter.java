package com.aiarchitect.terraquery.resilience;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight global per-minute limiter for chat ingress.
 */
@Component
public class SimpleRequestRateLimiter {

    private final int maxQueriesPerMinute;
    private final AtomicLong windowStartEpochMinute = new AtomicLong(currentMinute());
    private final AtomicInteger counter = new AtomicInteger(0);

    public SimpleRequestRateLimiter(AgentGuardrailsConfig guardrails) {
        this.maxQueriesPerMinute = guardrails.maxQueriesPerMinute();
    }

    public synchronized boolean tryAcquire() {
        long nowMinute = currentMinute();
        if (windowStartEpochMinute.get() != nowMinute) {
            windowStartEpochMinute.set(nowMinute);
            counter.set(0);
        }
        return counter.incrementAndGet() <= maxQueriesPerMinute;
    }

    private static long currentMinute() {
        return Instant.now().getEpochSecond() / 60L;
    }
}
