package ai.architect.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Provides a virtual-thread-per-task executor for all parallel agent calls.
 * Platform-level virtual thread support for Tomcat and Spring's async executor
 * is enabled via {@code spring.threads.virtual.enabled=true} in application.yml.
 */
@Configuration
public class VirtualThreadConfig {

    @Bean
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
