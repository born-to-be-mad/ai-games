package com.aiarchitect.terraquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures what gets persisted per conversation.
 * MESSAGES_ONLY: lightweight — only user + assistant messages.
 * FULL: debug-friendly — includes tool call arguments, raw results, latency.
 */
@ConfigurationProperties(prefix = "terra-query.conversation")
public record PersistenceScopeConfig(PersistenceScope persistenceScope) {

    public enum PersistenceScope {
        MESSAGES_ONLY,
        FULL
    }
}
