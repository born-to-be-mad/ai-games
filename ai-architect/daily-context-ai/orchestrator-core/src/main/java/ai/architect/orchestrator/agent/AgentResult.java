package ai.architect.orchestrator.agent;

/**
 * Result returned by a worker agent after execution.
 *
 * @param agentName    identifies the agent (e.g. "weather", "news")
 * @param content      agent's answer; null when success is false
 * @param success      true when the agent completed without error
 * @param errorMessage error description; null when success is true
 */
public record AgentResult(
        String agentName,
        String content,
        boolean success,
        String errorMessage
) {
    public static AgentResult success(String agentName, String content) {
        return new AgentResult(agentName, content, true, null);
    }

    public static AgentResult failure(String agentName, String errorMessage) {
        return new AgentResult(agentName, null, false, errorMessage);
    }
}
