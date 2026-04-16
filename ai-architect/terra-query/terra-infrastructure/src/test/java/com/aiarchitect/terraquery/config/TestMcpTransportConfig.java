package com.aiarchitect.terraquery.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestMcpTransportConfig {

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> testMcpClientCustomizer() {
        return (connectionName, builder) -> builder.openConnectionOnStartup(false);
    }
}
