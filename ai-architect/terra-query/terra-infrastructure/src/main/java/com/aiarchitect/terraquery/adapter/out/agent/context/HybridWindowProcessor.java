package com.aiarchitect.terraquery.adapter.out.agent.context;

import com.aiarchitect.terraquery.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Two-tier context window strategy (default):
 * 1. If history ≤ windowSize → pass everything verbatim (no overhead)
 * 2. If history > windowSize → slide first (cheap); if still oversized → summarize the remainder
 *
 * This is the recommended strategy: avoids the summarization LLM call for short conversations
 * and degrades gracefully as history grows.
 */
@Slf4j
@RequiredArgsConstructor
public class HybridWindowProcessor implements ContextWindowProcessor {

    private final SlidingWindowProcessor sliding;
    private final SummarizingWindowProcessor summarizing;

    @Override
    public List<ChatMessage> process(List<ChatMessage> history, int windowSize) {
        if (history.size() <= windowSize) {
            return history;
        }

        // Sliding covers the common case cheaply
        List<ChatMessage> afterSlide = sliding.process(history, windowSize);

        // If there are many messages dropped (> 2× window), add a summary prefix
        int dropped = history.size() - windowSize;
        if (dropped > windowSize) {
            log.debug("[Hybrid] {} messages dropped — attaching summarized prefix", dropped);
            return summarizing.process(history, windowSize);
        }

        return afterSlide;
    }
}
