package com.aiarchitect.terraquery.live;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Cross-provider evaluation configuration for live LLM tests.
 *
 * Generator: OpenAI gpt-4o-mini — produces answers from retrieved contexts.
 * Evaluator: Anthropic claude-haiku — scores answer quality (avoids self-evaluation bias).
 *
 * Both providers are configured in application-live-test.yml.
 * The primary ChatModel (used by the agent pipeline) is still selected
 * via terra-query.ai.provider — these beans are supplementary, named beans
 * used only by LiveLlmEvalTest for explicit cross-provider scoring.
 */
@TestConfiguration
public class EvalConfig {

    /** Generator model: OpenAI gpt-4o-mini — cheap, fast, produces answers. */
    @Bean("generatorChatModel")
    public ChatModel generatorChatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }

    /** Evaluator model: Anthropic claude-haiku — independent scorer, no self-eval bias. */
    @Bean("evaluatorChatModel")
    public ChatModel evaluatorChatModel(AnthropicChatModel anthropicChatModel) {
        return anthropicChatModel;
    }
}
