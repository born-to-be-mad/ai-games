package com.aiarchitect.terraquery.adapter.out.agent;

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
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Lightweight secondary validation on the Spring side.
 * Pydantic on the MCP server is the authoritative validator.
 *
 * Two defence layers:
 * 1. Schema-object substitution — some LLMs pass JSON Schema descriptors as values
 *    (e.g. {"type":"string"} instead of an actual string). Substitutes sensible defaults.
 * 2. Injection detection — rejects arguments containing known injection patterns.
 *
 * Based on SanitizingToolCallback pattern from daily-context-ai.
 */
@Slf4j
@Component
public class ToolArgumentSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern INJECTION_PATTERN =
            Pattern.compile("(?i)(\\$\\{|#\\{|<script|javascript:|__import__|eval\\(|exec\\()");
    private static final int MAX_ARGUMENT_LENGTH = 10_000;

    public ToolCallback wrap(ToolCallback original) {
        return new SanitizingToolCallback(original);
    }

    private static class SanitizingToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        SanitizingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            String sanitized = sanitizeInput(toolInput);
            log.debug("Tool '{}' args sanitized: {} → {}", getToolDefinition().name(), toolInput, sanitized);
            return delegate.call(sanitized);
        }

        private String sanitizeInput(String input) {
            if (input == null || input.isBlank()) return input;
            if (input.length() > MAX_ARGUMENT_LENGTH) {
                throw new IllegalArgumentException(
                        "Tool argument exceeds max length of " + MAX_ARGUMENT_LENGTH);
            }
            if (INJECTION_PATTERN.matcher(input).find()) {
                log.warn("Potential injection in tool args for '{}': {}",
                        getToolDefinition().name(), input);
                throw new SecurityException("Tool arguments contain disallowed patterns");
            }
            try {
                JsonNode root = MAPPER.readTree(input);
                if (!root.isObject()) return input;
                ObjectNode sanitized = MAPPER.createObjectNode();
                root.fields().forEachRemaining(e ->
                        sanitized.set(e.getKey(), substituteSchemaDescriptor(e.getValue())));
                return MAPPER.writeValueAsString(sanitized);
            } catch (JsonProcessingException e) {
                log.warn("Cannot parse tool args as JSON, passing through: {}", input);
                return input;
            }
        }

        /**
         * If node looks like a JSON Schema descriptor ({"type":"string",...}),
         * substitute a sensible default. Otherwise pass through as-is.
         */
        private JsonNode substituteSchemaDescriptor(JsonNode value) {
            if (value.isObject() && value.has("type")) {
                if (value.has("value")) return value.get("value");
                if (value.has("default")) return value.get("default");
                return switch (value.get("type").asText()) {
                    case "string"            -> TextNode.valueOf("");
                    case "integer", "number" -> IntNode.valueOf(0);
                    case "boolean"           -> BooleanNode.FALSE;
                    default                  -> value;
                };
            }
            return value;
        }
    }
}
