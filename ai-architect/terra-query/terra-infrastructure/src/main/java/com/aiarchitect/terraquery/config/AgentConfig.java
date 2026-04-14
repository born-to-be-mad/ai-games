package com.aiarchitect.terraquery.config;

import com.aiarchitect.terraquery.adapter.out.agent.context.ContextWindowProcessor;
import com.aiarchitect.terraquery.adapter.out.agent.context.HybridWindowProcessor;
import com.aiarchitect.terraquery.adapter.out.agent.context.SlidingWindowProcessor;
import com.aiarchitect.terraquery.adapter.out.agent.context.SummarizingWindowProcessor;
import com.aiarchitect.terraquery.port.out.ConversationRepository;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.port.in.ConversationUseCase;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.service.ChatService;
import com.aiarchitect.terraquery.service.ConversationService;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires domain services with their port implementations.
 * Keeps terra-core domain services unaware of Spring — beans constructed here.
 *
 * Provider selection: set {@code terra-query.ai.provider} to openai (default), anthropic, or ollama.
 * Only the selected provider's ChatModel is exposed as the primary bean used by all agents.
 * Switch provider with a single env var: TERRA_QUERY_AI_PROVIDER=anthropic
 */
@Configuration
public class AgentConfig {

    // ── Provider-agnostic ChatModel selection ────────────────────────────────────
    // Exactly one bean is created based on terra-query.ai.provider.
    // @Primary ensures it wins over any other ChatModel in the context.

    @Bean("selectedChatModel")
    @Primary
    @ConditionalOnProperty(name = "terra-query.ai.provider", havingValue = "openai", matchIfMissing = true)
    public ChatModel openAiChatModel(OpenAiChatModel model) {
        return model;
    }

    @Bean("selectedChatModel")
    @Primary
    @ConditionalOnProperty(name = "terra-query.ai.provider", havingValue = "anthropic")
    public ChatModel anthropicChatModel(AnthropicChatModel model) {
        return model;
    }

    @Bean("selectedChatModel")
    @Primary
    @ConditionalOnProperty(name = "terra-query.ai.provider", havingValue = "ollama")
    public ChatModel ollamaChatModel(OllamaChatModel model) {
        return model;
    }

    // ── Context window strategies ────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "terra-query.agent.guardrails.context-window-strategy",
            havingValue = "SLIDING_WINDOW")
    public ContextWindowProcessor slidingWindowProcessor() {
        return new SlidingWindowProcessor();
    }

    @Bean
    @ConditionalOnProperty(name = "terra-query.agent.guardrails.context-window-strategy",
            havingValue = "SUMMARIZING")
    public ContextWindowProcessor summarizingWindowProcessor(
            @Qualifier("selectedChatModel") ChatModel chatModel) {
        return new SummarizingWindowProcessor(chatModel);
    }

    @Bean
    @ConditionalOnProperty(name = "terra-query.agent.guardrails.context-window-strategy",
            havingValue = "HYBRID", matchIfMissing = true)
    public ContextWindowProcessor hybridWindowProcessor(
            @Qualifier("selectedChatModel") ChatModel chatModel) {
        SlidingWindowProcessor sliding = new SlidingWindowProcessor();
        SummarizingWindowProcessor summarizing = new SummarizingWindowProcessor(chatModel);
        return new HybridWindowProcessor(sliding, summarizing);
    }

    // ── Domain services ──────────────────────────────────────────────────────────

    @Bean
    public ChatUseCase chatUseCase(AgentPort agentPort,
                                   ConversationRepository conversationRepository,
                                   AgentGuardrailsConfig guardrails) {
        return new ChatService(agentPort, conversationRepository, guardrails.slidingWindowSize());
    }

    @Bean
    public ConversationUseCase conversationUseCase(ConversationRepository conversationRepository) {
        return new ConversationService(conversationRepository);
    }
}
