package ai.architect.orchestrator.service;

import ai.architect.orchestrator.agent.AgentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runs worker agents in parallel using virtual threads.
 * Each task is submitted to the virtual-thread-per-task executor via CompletableFuture.
 * Exceptions inside a task are caught and returned as {@link AgentResult#failure}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCoordinationService {

    private final Executor virtualThreadExecutor;

    public List<AgentResult> runParallel(List<Supplier<AgentResult>> tasks) {
        log.info("Dispatching {} agent task(s) in parallel", tasks.size());
        List<CompletableFuture<AgentResult>> futures = tasks.stream()
                .map(task -> CompletableFuture
                        .supplyAsync(task, virtualThreadExecutor)
                        .exceptionally(ex -> {
                            log.error("Agent task failed unexpectedly: {}", ex.getMessage(), ex);
                            return AgentResult.failure("unknown", ex.getMessage());
                        }))
                .toList();

        List<AgentResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long succeeded = results.stream().filter(AgentResult::success).count();
        log.info("Agent tasks completed: {}/{} succeeded", succeeded, results.size());
        return results;
    }
}
