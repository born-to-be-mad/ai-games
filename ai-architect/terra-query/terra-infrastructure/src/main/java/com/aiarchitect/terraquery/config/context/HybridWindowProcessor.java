package com.aiarchitect.terraquery.config.context;

import com.aiarchitect.terraquery.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Two-tier context window strategy (default):
 * 1. If history ≤ windowSize → pass everything verbatim (no overhead)
 * 2. If history > windowSize → slide first (cheap); if dropped > windowSize → summarize the remainder
 *
 * Avoids the summarization LLM call for short conversations and degrades
 * gracefully as history grows.
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

        int dropped = history.size() - windowSize;
        if (dropped > windowSize) {
            log.debug("[Hybrid] {} messages dropped — attaching summarized prefix", dropped);
            return summarizing.process(history, windowSize);
        }

        return sliding.process(history, windowSize);
    }
}
