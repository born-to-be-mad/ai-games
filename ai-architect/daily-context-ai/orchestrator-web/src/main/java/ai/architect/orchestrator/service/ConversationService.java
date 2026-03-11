package ai.architect.orchestrator.service;

import ai.architect.orchestrator.domain.Conversation;
import ai.architect.orchestrator.domain.Message;
import ai.architect.orchestrator.domain.MessageRole;
import ai.architect.orchestrator.dto.ChatRequest;
import ai.architect.orchestrator.dto.ChatResponse;
import ai.architect.orchestrator.dto.ConversationDTO;
import ai.architect.orchestrator.dto.MessageDTO;
import ai.architect.orchestrator.repository.ConversationRepository;
import ai.architect.orchestrator.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OrchestratorService orchestratorService;

    @Transactional
    public ChatResponse processChat(ChatRequest req) {
        Conversation conversation = resolveConversation(req.conversationId());
        log.info("Processing chat: conversationId={} query='{}'", conversation.getId(), req.query());

        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.USER)
                .content(req.query())
                .build());

        long startTime = System.currentTimeMillis();

        Set<String> weatherProviders = req.weatherProviders() != null ? req.weatherProviders() : Collections.emptySet();
        Set<String> newsSources = req.newsSources() != null ? req.newsSources() : Collections.emptySet();
        String answer = orchestratorService.process(req.query(), weatherProviders, newsSources);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Orchestrator responded in {}ms for conversationId={}", durationMs, conversation.getId());

        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(answer)
                .build());

        if (conversation.getTopic() == null || conversation.getTopic().isBlank()) {
            String topic = req.query().length() > 50 ? req.query().substring(0, 50) : req.query();
            conversation.setTopic(topic);
            conversationRepository.save(conversation);
            log.info("Set topic='{}' for conversationId={}", topic, conversation.getId());
        }

        return new ChatResponse(conversation.getId(), answer, durationMs);
    }

    public List<ConversationDTO> listConversations() {
        List<ConversationDTO> result = conversationRepository.findAllByOrderByTimestampDesc().stream()
                .map(c -> new ConversationDTO(c.getId(), c.getTimestamp(), c.getTopic(), Collections.emptyList()))
                .toList();
        log.info("Listed {} conversation(s)", result.size());
        return result;
    }

    public ConversationDTO getConversation(UUID id) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id));

        List<MessageDTO> messages = messageRepository.findByConversationIdOrderByTimestamp(id).stream()
                .map(m -> new MessageDTO(m.getId(), m.getRole().name(), m.getContent(), m.getTimestamp()))
                .toList();

        log.info("Fetched conversationId={} with {} message(s)", id, messages.size());
        return new ConversationDTO(conversation.getId(), conversation.getTimestamp(), conversation.getTopic(), messages);
    }

    @Transactional
    public void deleteConversation(UUID id) {
        if (!conversationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        messageRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
        log.info("Deleted conversationId={}", id);
    }

    private Conversation resolveConversation(UUID conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseGet(() -> {
                        log.info("ConversationId={} not found, creating new", conversationId);
                        return conversationRepository.save(Conversation.builder().build());
                    });
        }
        Conversation created = conversationRepository.save(Conversation.builder().build());
        log.info("Created new conversationId={}", created.getId());
        return created;
    }
}
