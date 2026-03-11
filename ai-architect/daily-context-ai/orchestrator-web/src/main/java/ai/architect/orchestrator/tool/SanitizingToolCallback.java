package ai.architect.orchestrator.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps a {@link ToolCallback} and sanitizes tool arguments before delegating the call.
 *
 * <p>Some LLMs (e.g. Qwen, smaller Ollama models) pass JSON schema descriptors as argument values
 * instead of actual values — e.g. {@code {"type":"string"}} instead of {@code "latest tech news"}.
 * This wrapper detects such schema objects and substitutes sensible defaults so that the
 * downstream MCP server receives valid arguments.
 */
@Slf4j
public class SanitizingToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolCallback delegate;

    public SanitizingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String sanitized = sanitizeInput(toolInput);
        log.debug("Tool '{}' raw args: {} → sanitized: {}", getToolDefinition().name(), toolInput, sanitized);
        return delegate.call(sanitized);
    }

    private String sanitizeInput(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return toolInput;
        }
        try {
            JsonNode root = MAPPER.readTree(toolInput);
            if (!root.isObject()) {
                return toolInput;
            }
            ObjectNode sanitizedNode = MAPPER.createObjectNode();
            root.fields().forEachRemaining(entry ->
                    sanitizedNode.set(entry.getKey(), sanitize(entry.getValue()))
            );
            return MAPPER.writeValueAsString(sanitizedNode);
        } catch (JsonProcessingException e) {
            log.warn("Tool '{}' could not parse args as JSON, passing through: {}", getToolDefinition().name(), toolInput);
            return toolInput;
        }
    }

    /**
     * If the node looks like a JSON Schema descriptor ({@code {"type":"string",...}}),
     * substitute a sensible default. Otherwise pass through as-is.
     */
    private JsonNode sanitize(JsonNode value) {
        if (value.isObject() && value.has("type")) {
            if (value.has("value")) return value.get("value");
            if (value.has("default")) return value.get("default");
            return switch (value.get("type").asText()) {
                case "string"           -> TextNode.valueOf("");
                case "integer", "number" -> IntNode.valueOf(0);
                case "boolean"          -> BooleanNode.FALSE;
                default                 -> value;
            };
        }
        return value;
    }
}
