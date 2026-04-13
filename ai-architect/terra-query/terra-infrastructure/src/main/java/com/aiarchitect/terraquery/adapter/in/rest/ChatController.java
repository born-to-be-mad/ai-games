package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.streaming.ChatEvent;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatUseCase chatUseCase;
    private final ToolProgressIndicator progressIndicator;

    /**
     * Blocking chat endpoint — waits for the full agent response.
     * Use for simple integrations or testing.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request, conversationId={}", request.conversationId());
        AgentResponse response = chatUseCase.chat(request.message(), request.conversationId());
        return ResponseEntity.ok(ChatResponse.from(response, request.conversationId()));
    }

    /**
     * SSE streaming endpoint — emits typed events as the agent works.
     * Frontend subscribes via EventSource and receives real-time tool progress + answer chunks.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> streamChat(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request, conversationId={}", request.conversationId());

        Flux<ServerSentEvent<ChatEvent>> progressEvents = progressIndicator.events()
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.type().name())
                        .data(event)
                        .build());

        Flux<ServerSentEvent<ChatEvent>> agentExecution = Mono
                .fromCallable(() -> chatUseCase.chat(request.message(), request.conversationId()))
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

    // DTO records
    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message,
            String conversationId
    ) {}

    public record ChatResponse(
            String conversationId,
            String answer,
            List<String> toolsUsed,
            List<String> sources,
            List<String> agentChain
    ) {
        static ChatResponse from(AgentResponse response, String conversationId) {
            return new ChatResponse(
                    conversationId,
                    response.answer(),
                    response.toolsUsed(),
                    response.sources(),
                    response.agentChain()
            );
        }
    }
}
