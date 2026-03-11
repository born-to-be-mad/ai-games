package ai.architect.orchestrator.service;

import ai.architect.orchestrator.agent.AgentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCoordinationServiceTest {

    private AgentCoordinationService service;

    @BeforeEach
    void setUp() {
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        service = new AgentCoordinationService(executor);
    }

    @Test
    void runParallel_allSucceed_returnsAllResults() {
        List<Supplier<AgentResult>> tasks = List.of(
                () -> AgentResult.success("weather", "Sunny, 20°C"),
                () -> AgentResult.success("news", "Top headlines today")
        );

        List<AgentResult> results = service.runParallel(tasks);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(AgentResult::success);
        assertThat(results).extracting(AgentResult::agentName)
                .containsExactlyInAnyOrder("weather", "news");
    }

    @Test
    void runParallel_taskThrowsException_returnsFailureResult() {
        List<Supplier<AgentResult>> tasks = List.of(
                () -> { throw new RuntimeException("MCP unreachable"); }
        );

        List<AgentResult> results = service.runParallel(tasks);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isFalse();
        assertThat(results.getFirst().agentName()).isEqualTo("unknown");
        assertThat(results.getFirst().errorMessage()).contains("MCP unreachable");
    }

    @Test
    void runParallel_mixedSuccessAndFailure_returnsBoth() {
        List<Supplier<AgentResult>> tasks = List.of(
                () -> AgentResult.success("weather", "Rainy"),
                () -> AgentResult.failure("news", "API timeout"),
                () -> { throw new RuntimeException("Unexpected"); }
        );

        List<AgentResult> results = service.runParallel(tasks);

        assertThat(results).hasSize(3);
        long succeeded = results.stream().filter(AgentResult::success).count();
        assertThat(succeeded).isEqualTo(1);
    }

    @Test
    void runParallel_emptyTaskList_returnsEmptyList() {
        List<AgentResult> results = service.runParallel(List.of());

        assertThat(results).isEmpty();
    }

    @Test
    void runParallel_singleTask_returnsOneResult() {
        List<Supplier<AgentResult>> tasks = List.of(
                () -> AgentResult.success("weather", "Clear skies")
        );

        List<AgentResult> results = service.runParallel(tasks);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getFirst().content()).isEqualTo("Clear skies");
    }
}
