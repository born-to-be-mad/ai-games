package com.aiarchitect.rag.report.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a baseline Jackson mapper for components that deserialize JSON resources
 * (for example the golden evaluation dataset) even if framework auto-configuration
 * does not expose an ObjectMapper bean.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
