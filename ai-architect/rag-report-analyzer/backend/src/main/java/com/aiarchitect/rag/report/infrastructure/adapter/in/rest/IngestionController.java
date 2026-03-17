package com.aiarchitect.rag.report.infrastructure.adapter.in.rest;

import com.aiarchitect.rag.report.domain.port.in.ReportIngestionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Inbound adapter: accepts a multipart PDF upload, persists it to a temp file,
 * and delegates to the ingestion pipeline.
 *
 * <pre>
 * POST /api/v1/ingest
 *   Content-Type: multipart/form-data
 *   Parts: file (PDF), ticker, year, quarter
 *
 * Response:
 * {
 *   "message": "Ingested NVDA 2025 Annual — 127 chunks created",
 *   "chunksCreated": 127
 * }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final ReportIngestionFacade reportIngestionFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam String ticker,
            @RequestParam int year,
            @RequestParam String quarter) {

        log.info("POST /api/v1/ingest — ticker={}, year={}, quarter={}, file={}",
                ticker, year, quarter, file.getOriginalFilename());

        Path tempFile = saveTempFile(file);
        try {
            List<Document> chunks = reportIngestionFacade.ingest(
                    tempFile.toAbsolutePath().toString(), ticker, year, quarter);

            String message = "Ingested %s %d %s — %d chunks created"
                    .formatted(ticker, year, quarter, chunks.size());
            log.info(message);

            return Map.of("message", message, "chunksCreated", chunks.size());
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private Path saveTempFile(MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("rag-ingest-", ".pdf");
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save uploaded PDF to temp file", e);
        }
    }

    private void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("Could not delete temp file {}: {}", tempFile, e.getMessage());
        }
    }
}
