package com.aiarchitect.terraquery.port.in;

import com.aiarchitect.terraquery.model.AgentResponse;

/**
 * Inbound port — primary use case for chatting with the TerraQuery agent.
 */
public interface ChatUseCase {

    /**
     * Process a user message and return the agent's response.
     *
     * @param message        the user's natural language query
     * @param conversationId existing conversation ID to continue, or null to start a new one
     * @return structured agent response with answer, tools used, and sources
     */
    AgentResponse chat(String message, String conversationId);
}
