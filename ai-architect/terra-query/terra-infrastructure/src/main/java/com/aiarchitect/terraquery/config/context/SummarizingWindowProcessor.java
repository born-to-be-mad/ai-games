package com.aiarchitect.terraquery.config.context;

import com.aiarchitect.terraquery.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Summarizes messages that fall outside the sliding window using an LLM call.
 * The summary is injected as a USER message prepended to the windowed history,
 * preserving context from the dropped portion without exceeding token limits.
 *
 * Trade-off: one extra LLM call per turn when history exceeds the window.
 * Use HYBRID strategy to amortize this cost.
 */
@Slf4j
@RequiredArgsConstructor
public class SummarizingWindowProcessor implements ContextWindowProcessor {

    static final String SUMMARIZATION_PROMPT = """
            Summarize the following conversation history concisely.
            Preserve key facts, entities, and decisions discussed.
            Write the summary in third person, past tense.
            Keep it under 200 words.

            Conversation:
            %s
            """;

    private final ChatClient summaryClient;

    public SummarizingWindowProcessor(ChatModel chatModel) {
        this.summaryClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public List<ChatMessage> process(List<ChatMessage> history, int windowSize) {
        if (history.size() <= windowSize) {
            return history;
        }

        List<ChatMessage> older = history.subList(0, history.size() - windowSize);
        List<ChatMessage> recent = history.subList(history.size() - windowSize, history.size());

        String summary = summarize(older);
        log.debug("[SummarizingWindow] Summarized {} old messages into context prefix", older.size());

        // Inject summary as a synthetic USER message at the front
        List<ChatMessage> result = new ArrayList<>();
        result.add(ChatMessage.userMessage(
                recent.get(0).conversationId(),
                "[Context from earlier in conversation]: " + summary
        ));
        result.addAll(recent);
        return result;
    }

    private String summarize(List<ChatMessage> messages) {
        StringBuilder transcript = new StringBuilder();
        for (ChatMessage m : messages) {
            transcript.append("[").append(m.role()).append("]: ").append(m.content()).append("\n");
        }
        try {
            return summaryClient.prompt()
                    .user(SUMMARIZATION_PROMPT.formatted(transcript))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[SummarizingWindow] Summary LLM call failed, falling back to truncation: {}", e.getMessage());
            return "Earlier conversation contained " + messages.size() + " messages (summary unavailable).";
        }
    }
}
