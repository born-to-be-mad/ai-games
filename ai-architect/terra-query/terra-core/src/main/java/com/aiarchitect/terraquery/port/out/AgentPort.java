package com.aiarchitect.terraquery.port.out;

import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.model.ChatMessage;

import java.util.List;

/**
 * Outbound port — the domain's interface to the multi-agent runtime.
 * Implementations live in terra-infrastructure and use Spring AI.
 */
public interface AgentPort {

    /**
     * Execute the multi-agent pipeline for a user query.
     *
     * @param userQuery the user's natural language question
     * @param history   previous messages in the conversation (may be windowed)
     * @return structured agent response
     */
    AgentResponse execute(String userQuery, List<ChatMessage> history);
}
