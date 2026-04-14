package com.aiarchitect.terraquery.adapter.out.agent.context;

import com.aiarchitect.terraquery.model.ChatMessage;

import java.util.List;

/**
 * Reduces conversation history to fit within the model's context window.
 * Applied by SupervisorAgentAdapter before passing history to sub-agents.
 */
public interface ContextWindowProcessor {

    /**
     * Process the full conversation history and return a version that fits the context limit.
     *
     * @param history full message history (oldest first)
     * @param windowSize maximum number of recent messages to keep verbatim
     * @return reduced history, oldest-first ordering preserved
     */
    List<ChatMessage> process(List<ChatMessage> history, int windowSize);
}
