package com.aiarchitect.terraquery.adapter.out.agent.context;

import com.aiarchitect.terraquery.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Keeps the last {@code windowSize} messages verbatim; older messages are dropped.
 * Simplest strategy — no additional LLM calls, zero latency overhead.
 */
@Slf4j
public class SlidingWindowProcessor implements ContextWindowProcessor {

    @Override
    public List<ChatMessage> process(List<ChatMessage> history, int windowSize) {
        if (history.size() <= windowSize) {
            return history;
        }
        List<ChatMessage> windowed = history.subList(history.size() - windowSize, history.size());
        log.debug("[SlidingWindow] Dropped {} old messages, kept last {}",
                history.size() - windowSize, windowSize);
        return windowed;
    }
}
