package com.aiarchitect.terraquery.streaming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Publishes tool-call progress events to the SSE stream.
 * Agents call this during their execution to emit real-time progress
 * (e.g. "Searching historical database...") to the frontend.
 */
@Slf4j
@Component
public class ToolProgressIndicator {

    private final Sinks.Many<ChatEvent> sink;

    public ToolProgressIndicator() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public void toolCallStarted(String agentName, String toolName) {
        log.debug("[{}] Tool call started: {}", agentName, toolName);
        emit(new ChatEvent.ToolCallStart(toolName, agentName));
    }

    public void toolCallCompleted(String agentName, String toolName, String resultPreview) {
        log.debug("[{}] Tool call completed: {} → {}", agentName, toolName, resultPreview);
        emit(new ChatEvent.ToolCallEnd(toolName, agentName, resultPreview));
    }

    public void agentThinking(String agentName, String status) {
        log.debug("[{}] Thinking: {}", agentName, status);
        emit(new ChatEvent.AgentThinking(agentName, status));
    }

    public Flux<ChatEvent> events() {
        return sink.asFlux();
    }

    private void emit(ChatEvent event) {
        var result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit SSE event {}: {}", event.type(), result);
        }
    }
}
