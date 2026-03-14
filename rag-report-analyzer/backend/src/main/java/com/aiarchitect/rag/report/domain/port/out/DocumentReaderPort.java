package com.aiarchitect.rag.report.domain.port.out;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Outbound port: reads a source file and splits it into chunks.
 */
public interface DocumentReaderPort {

    List<Document> readAndChunk(String filePath);
}
