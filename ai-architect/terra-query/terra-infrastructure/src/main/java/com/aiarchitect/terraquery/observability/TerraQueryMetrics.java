package com.aiarchitect.terraquery.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer metrics for TerraQuery agent pipeline.
 *
 * Exposed at /actuator/prometheus and scraped by Grafana.
 *
 * Metrics:
 *   terra_query_agent_calls_total{agent, outcome}  — agent invocation counter
 *   terra_query_chat_duration_seconds              — end-to-end latency histogram
 *   terra_query_context_window_strategy_total{strategy} — context strategy usage
 */
@Component
public class TerraQueryMetrics {

    private static final String PREFIX = "terra_query";

    private final MeterRegistry registry;

    // Pre-allocated counters (cheaper than looking up on every call)
    private final Counter supervisorSuccessCounter;
    private final Counter supervisorFailureCounter;
    private final Counter retrievalSuccessCounter;
    private final Counter retrievalFailureCounter;
    private final Counter analysisSuccessCounter;
    private final Counter analysisFailureCounter;

    private final Timer chatDurationTimer;

    public TerraQueryMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.supervisorSuccessCounter = agentCounter("SupervisorAgent", "success");
        this.supervisorFailureCounter = agentCounter("SupervisorAgent", "failure");
        this.retrievalSuccessCounter  = agentCounter("DataRetrievalAgent", "success");
        this.retrievalFailureCounter  = agentCounter("DataRetrievalAgent", "failure");
        this.analysisSuccessCounter   = agentCounter("AnalysisSynthesisAgent", "success");
        this.analysisFailureCounter   = agentCounter("AnalysisSynthesisAgent", "failure");

        this.chatDurationTimer = Timer.builder(PREFIX + ".chat.duration.seconds")
                .description("End-to-end chat request latency (full agent pipeline)")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    // ── Agent counters ────────────────────────────────────────────────────────

    public void recordSupervisorSuccess() { supervisorSuccessCounter.increment(); }
    public void recordSupervisorFailure() { supervisorFailureCounter.increment(); }
    public void recordRetrievalSuccess()  { retrievalSuccessCounter.increment(); }
    public void recordRetrievalFailure()  { retrievalFailureCounter.increment(); }
    public void recordAnalysisSuccess()   { analysisSuccessCounter.increment(); }
    public void recordAnalysisFailure()   { analysisFailureCounter.increment(); }

    // ── Chat timer ────────────────────────────────────────────────────────────

    /**
     * Record end-to-end chat duration. Typical call site:
     * <pre>
     *   long start = System.nanoTime();
     *   // ... agent pipeline ...
     *   metrics.recordChatDuration(System.nanoTime() - start, TimeUnit.NANOSECONDS);
     * </pre>
     */
    public void recordChatDuration(long amount, TimeUnit unit) {
        chatDurationTimer.record(amount, unit);
    }

    // ── Context window strategy counter ───────────────────────────────────────

    public void recordContextWindowStrategy(String strategy) {
        Counter.builder(PREFIX + ".context.window.strategy.total")
                .description("Context window strategy activations")
                .tag("strategy", strategy)
                .register(registry)
                .increment();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Counter agentCounter(String agent, String outcome) {
        return Counter.builder(PREFIX + ".agent.calls.total")
                .description("Agent invocation outcomes")
                .tag("agent", agent)
                .tag("outcome", outcome)
                .register(registry);
    }
}
