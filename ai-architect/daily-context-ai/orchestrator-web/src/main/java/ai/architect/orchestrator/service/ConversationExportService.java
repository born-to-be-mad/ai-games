package ai.architect.orchestrator.service;

import ai.architect.orchestrator.domain.Conversation;
import ai.architect.orchestrator.domain.Message;
import ai.architect.orchestrator.repository.ConversationRepository;
import ai.architect.orchestrator.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationExportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public byte[] exportJson(UUID id) throws Exception {
        Conversation conversation = findConversation(id);
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestamp(id);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("id", conversation.getId());
        export.put("topic", conversation.getTopic());
        export.put("createdAt", conversation.getTimestamp());
        export.put("exportedAt", LocalDateTime.now());
        export.put("messageCount", messages.size());
        export.put("messages", messages.stream().map(m -> {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.getRole().name());
            msg.put("content", m.getContent());
            msg.put("timestamp", m.getTimestamp());
            return msg;
        }).toList());

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);

        return mapper.writeValueAsBytes(export);
    }

    public byte[] exportPdf(UUID id) throws Exception {
        Conversation conversation = findConversation(id);
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestamp(id);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Title page
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        Paragraph title = new Paragraph("Daily Context AI — Conversation Export", titleFont);
        title.setSpacingAfter(12);
        document.add(title);

        document.add(new Paragraph("Topic: " + (conversation.getTopic() != null ? conversation.getTopic() : "(untitled)"), metaFont));
        document.add(new Paragraph("Created: " + conversation.getTimestamp().format(FORMATTER), metaFont));
        document.add(new Paragraph("Messages: " + messages.size(), metaFont));
        document.add(new Paragraph("Exported: " + LocalDateTime.now().format(FORMATTER), metaFont));
        document.add(new Paragraph("Conversation ID: " + conversation.getId(), metaFont));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("─".repeat(80), metaFont));
        document.add(Chunk.NEWLINE);

        // Messages
        for (Message message : messages) {
            Paragraph roleLabel = new Paragraph(message.getRole().name(), headerFont);
            roleLabel.setSpacingBefore(8);
            document.add(roleLabel);

            Paragraph content = new Paragraph(message.getContent(), bodyFont);
            content.setSpacingAfter(4);
            document.add(content);

            document.add(new Paragraph(message.getTimestamp().format(FORMATTER), metaFont));
            document.add(new Paragraph("─".repeat(80), metaFont));
        }

        document.close();
        log.info("Exported conversationId={} as PDF ({} messages)", id, messages.size());
        return baos.toByteArray();
    }

    private Conversation findConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id));
    }
}
