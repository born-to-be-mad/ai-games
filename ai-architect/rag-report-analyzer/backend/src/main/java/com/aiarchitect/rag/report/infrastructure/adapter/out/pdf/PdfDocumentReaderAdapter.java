package com.aiarchitect.rag.report.infrastructure.adapter.out.pdf;

import com.aiarchitect.rag.report.domain.port.out.DocumentReaderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbound adapter: reads a PDF file using Spring AI's {@link PagePdfDocumentReader}
 * and splits it into chunks via {@link TokenTextSplitter}.
 *
 * <p>Chunk size 512 tokens with 64-token overlap balances context completeness
 * with embedding quality. Smaller chunks increase precision; larger chunks improve recall.
 */
@Slf4j
@Component
public class PdfDocumentReaderAdapter implements DocumentReaderPort {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int MIN_CHUNK_SIZE = 64;

    @Override
    public List<Document> readAndChunk(String filePath) {
        log.debug("Reading PDF: {}", filePath);

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)  // one doc per page preserves page metadata
                .build();

        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                new FileSystemResource(filePath), config);

        List<Document> pages = reader.read();
        log.debug("Read {} pages from {}", pages.size(), filePath);

        // Punctuation marks used as preferred split boundaries
        TokenTextSplitter splitter = new TokenTextSplitter(
                CHUNK_SIZE, CHUNK_OVERLAP, MIN_CHUNK_SIZE, 10_000, true,
                List.of('.', '?', '!', '\n', ',', ';'));

        List<Document> chunks = splitter.apply(pages);
        log.debug("Split into {} chunks (size={}, overlap={})", chunks.size(), CHUNK_SIZE, CHUNK_OVERLAP);

        return chunks;
    }
}
