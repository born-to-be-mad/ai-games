package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.dto.ConversationDTO;
import ai.architect.orchestrator.service.ConversationExportService;
import ai.architect.orchestrator.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationExportService conversationExportService;

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> listConversations() {
        return ResponseEntity.ok(conversationService.listConversations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversation(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export/json")
    public ResponseEntity<byte[]> exportJson(@PathVariable UUID id) throws Exception {
        byte[] data = conversationExportService.exportJson(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("conversation-" + id + ".json").build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) throws Exception {
        byte[] data = conversationExportService.exportPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("conversation-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
