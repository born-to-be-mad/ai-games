package com.aiarchitect.rag.report.domain.model;

/**
 * Thrown when an LLM call fails after all retry attempts are exhausted.
 *
 * <p>Raised by the {@code @Recover} fallback in LLM adapters once Spring Retry
 * has given up. Callers (controllers) may catch this to return a 503 response.
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
