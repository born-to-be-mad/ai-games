package com.aiarchitect.terraquery.streaming;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed hierarchy for typed SSE events streamed to the frontend.
 * Each subtype maps to a named SSE event (event: TOOL_CALL_START, etc.)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatEvent.ToolCallStart.class, name = "TOOL_CALL_START"),
    @JsonSubTypes.Type(value = ChatEvent.ToolCallEnd.class, name = "TOOL_CALL_END"),
    @JsonSubTypes.Type(value = ChatEvent.AgentThinking.class, name = "AGENT_THINKING"),
    @JsonSubTypes.Type(value = ChatEvent.AnswerChunk.class, name = "ANSWER_CHUNK"),
    @JsonSubTypes.Type(value = ChatEvent.AnswerComplete.class, name = "ANSWER_COMPLETE")
})
public sealed interface ChatEvent
        permits ChatEvent.ToolCallStart, ChatEvent.ToolCallEnd,
                ChatEvent.AgentThinking, ChatEvent.AnswerChunk, ChatEvent.AnswerComplete {

    EventType type();

    enum EventType { TOOL_CALL_START, TOOL_CALL_END, AGENT_THINKING, ANSWER_CHUNK, ANSWER_COMPLETE }

    /** Emitted when an agent begins executing a tool call. */
    record ToolCallStart(String tool, String agent) implements ChatEvent {
        @Override public EventType type() { return EventType.TOOL_CALL_START; }
    }

    /** Emitted when a tool call completes. */
    record ToolCallEnd(String tool, String agent, String resultPreview) implements ChatEvent {
        @Override public EventType type() { return EventType.TOOL_CALL_END; }
    }

    /** Emitted when an agent switches to synthesis/reasoning mode. */
    record AgentThinking(String agent, String status) implements ChatEvent {
        @Override public EventType type() { return EventType.AGENT_THINKING; }
    }

    /** Emitted for each streamed token of the final answer. */
    record AnswerChunk(String text) implements ChatEvent {
        @Override public EventType type() { return EventType.ANSWER_CHUNK; }
    }

    /** Emitted once when the full answer is assembled; carries metadata. */
    record AnswerComplete(
            java.util.List<String> toolsUsed,
            java.util.List<String> sources,
            java.util.List<String> agentChain
    ) implements ChatEvent {
        @Override public EventType type() { return EventType.ANSWER_COMPLETE; }
    }
}
