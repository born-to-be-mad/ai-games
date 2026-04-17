package com.aiarchitect.terraquery.streaming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Publishes tool-call progress events to the active SSE stream.
 * <p>
 * Each {@code /chat/stream} call creates its own {@link Sinks.Many}; {@link #activate} binds
 * emissions from agents (any thread) to that sink until {@link #deactivate}. A lock serializes
 * concurrent streaming chats so progress is never routed to the wrong client.
 */
@Slf4j
@Component
public class ToolProgressIndicator {

    private final ReentrantLock streamBindingLock = new ReentrantLock();
    private Sinks.Many<ChatEvent> activeSink;

    /**
     * Binds subsequent {@code tool*} / {@code agent*} emissions to {@code sink}.
     * Blocks if another stream currently holds the binding.
     */
    public void activate(Sinks.Many<ChatEvent> sink) {
        streamBindingLock.lock();
        this.activeSink = sink;
    }

    /** Releases the binding created by {@link #activate}; must be paired on the same thread. */
    public void deactivate() {
        try {
            this.activeSink = null;
        } finally {
            streamBindingLock.unlock();
        }
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

    private void emit(ChatEvent event) {
        Sinks.Many<ChatEvent> sink = this.activeSink;
        if (sink == null) {
            log.debug("No active stream sink — dropping progress {}", event.type());
            return;
        }
        var result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit SSE event {}: {}", event.type(), result);
        }
    }
}
