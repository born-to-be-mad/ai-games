package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.api.model.ChatRequest;
import com.aiarchitect.terraquery.api.model.ChatResponse;
import com.aiarchitect.terraquery.api.model.Source;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.resilience.SimpleRequestRateLimiter;
import com.aiarchitect.terraquery.streaming.ChatEvent;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "chat", description = "Chat and streaming response endpoints")
public class ChatController {

    private final ChatUseCase chatUseCase;
    private final ToolProgressIndicator progressIndicator;
    private final SimpleRequestRateLimiter rateLimiter;
    private static final Duration STREAM_KEEPALIVE_INTERVAL = Duration.ofSeconds(10);
    private static final Duration STREAM_RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        enforceRateLimit();
        log.info("Received chat request, conversationId={}", request.getConversationId());
        String conversationId = request.getConversationId() != null
                ? request.getConversationId().toString() : null;
        AgentResponse response = chatUseCase.chat(request.getMessage(), conversationId);
        return ResponseEntity.ok(toResponse(response, request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> streamChat(@Valid @RequestBody ChatRequest request) {
        enforceRateLimit();
        log.info("Received streaming chat request, conversationId={}", request.getConversationId());
        String conversationId = request.getConversationId() != null
                ? request.getConversationId().toString() : null;

        Flux<ServerSentEvent<ChatEvent>> progressEvents = progressIndicator.events()
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.type().name())
                        .data(event)
                        .build());

        Flux<ServerSentEvent<ChatEvent>> keepAlive = Flux.interval(STREAM_KEEPALIVE_INTERVAL)
                .map(ignored -> ServerSentEvent.<ChatEvent>builder()
                        .comment("keepalive")
                        .build());

        Flux<ServerSentEvent<ChatEvent>> agentExecution = Mono
                .fromCallable(() -> chatUseCase.chat(request.getMessage(), conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(response -> Flux.just(
                        ServerSentEvent.<ChatEvent>builder()
                                .event(ChatEvent.EventType.ANSWER_CHUNK.name())
                                .data(new ChatEvent.AnswerChunk(response.answer()))
                                .build(),
                        ServerSentEvent.<ChatEvent>builder()
                                .event(ChatEvent.EventType.ANSWER_COMPLETE.name())
                                .data(new ChatEvent.AnswerComplete(
                                        response.toolsUsed(),
                                        response.sources(),
                                        response.agentChain()))
                                .build()
                ));

        // Serialize merged emissions so TOOL_CALL_* / AGENT_THINKING events are not lost to
        // cancellation races when ANSWER_COMPLETE arrives from the agent branch.
        return Flux.merge(progressEvents, keepAlive, agentExecution)
                .publishOn(Schedulers.boundedElastic(), 1)
                .takeUntil(event -> ChatEvent.EventType.ANSWER_COMPLETE.name().equals(event.event()))
                .timeout(STREAM_RESPONSE_TIMEOUT)
                .onErrorResume(TimeoutException.class, ex -> {
                    log.warn("SSE stream timed out before answer completion", ex);
                    return Flux.just(
                            ServerSentEvent.<ChatEvent>builder()
                                    .event(ChatEvent.EventType.ANSWER_CHUNK.name())
                                    .data(new ChatEvent.AnswerChunk(
                                            "I hit a timeout while processing this request. Please try again."
                                    ))
                                    .build(),
                            ServerSentEvent.<ChatEvent>builder()
                                    .event(ChatEvent.EventType.ANSWER_COMPLETE.name())
                                    .data(new ChatEvent.AnswerComplete(List.of(), List.of(), List.of()))
                                    .build()
                    );
                });
    }

    private ChatResponse toResponse(AgentResponse agentResponse, ChatRequest request) {
        List<Source> sources = agentResponse.sources().stream()
                .map(s -> new Source().name(s))
                .toList();
        return new ChatResponse()
                .conversationId(request.getConversationId())
                .answer(agentResponse.answer())
                .sources(sources)
                .toolsUsed(agentResponse.toolsUsed())
                .agentChain(agentResponse.agentChain());
    }

    private void enforceRateLimit() {
        if (!rateLimiter.tryAcquire()) {
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please retry shortly."
            );
        }
    }
}
