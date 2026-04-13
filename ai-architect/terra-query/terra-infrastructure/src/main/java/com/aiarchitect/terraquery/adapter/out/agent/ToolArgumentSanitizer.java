package com.aiarchitect.terraquery.adapter.out.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackWrapper;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Lightweight secondary validation on the Spring side.
 * Pydantic on the MCP server is the authoritative validator.
 * This catches LLM hallucinated schema objects and injection attempts
 * before the call reaches the MCP server.
 *
 * Reuses the SanitizingToolCallback pattern from daily-context-ai.
 */
@Slf4j
@Component
public class ToolArgumentSanitizer {

    private static final Pattern INJECTION_PATTERN =
            Pattern.compile("(?i)(\\$\\{|#\\{|<script|javascript:|__import__|eval\\(|exec\\()");
    private static final int MAX_ARGUMENT_LENGTH = 10_000;

    public ToolCallback wrap(ToolCallback original) {
        return new SanitizingToolCallback(original);
    }

    private class SanitizingToolCallback extends ToolCallbackWrapper {

        SanitizingToolCallback(ToolCallback delegate) {
            super(delegate);
        }

        @Override
        public String call(String toolInput) {
            validate(toolInput);
            return super.call(toolInput);
        }

        private void validate(String input) {
            if (input == null) return;
            if (input.length() > MAX_ARGUMENT_LENGTH) {
                log.warn("Tool argument too long ({} chars), truncating to {}", input.length(), MAX_ARGUMENT_LENGTH);
                throw new IllegalArgumentException(
                        "Tool argument exceeds maximum length of " + MAX_ARGUMENT_LENGTH);
            }
            if (INJECTION_PATTERN.matcher(input).find()) {
                log.warn("Potential injection attempt in tool arguments: {}", input);
                throw new SecurityException("Tool arguments contain disallowed patterns");
            }
        }
    }
}
