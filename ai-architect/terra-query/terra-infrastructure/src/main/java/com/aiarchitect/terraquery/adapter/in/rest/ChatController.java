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

import java.time.Duration;
import java.util.List;
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

        Flux<ServerSentEvent<ChatEvent>> agentExecution = Mono
                .fromCallable(() -> chatUseCase.chat(request.getMessage(), conversationId))
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

        return Flux.merge(progressEvents, agentExecution)
                .timeout(Duration.ofSeconds(120));
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
