package com.aiarchitect.rag.report.infrastructure.adapter.out.eval;

import com.aiarchitect.rag.report.domain.model.eval.GoldenQaItem;
import com.aiarchitect.rag.report.domain.port.out.GoldenDatasetPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Outbound adapter: loads the golden Q&A dataset from {@code classpath:eval/golden-dataset.json}.
 *
 * <p>The dataset is parsed once at startup and cached in memory.
 * Jackson deserializes JSON objects into {@link GoldenQaItem} records using the parameter-names module
 * (auto-configured by Spring Boot).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonGoldenDatasetAdapter implements GoldenDatasetPort {

    private final ObjectMapper objectMapper;

    @Value("classpath:eval/golden-dataset.json")
    private Resource datasetResource;

    private List<GoldenQaItem> cachedItems;

    @PostConstruct
    void load() throws IOException {
        cachedItems = objectMapper.readValue(
                datasetResource.getInputStream(),
                new TypeReference<List<GoldenQaItem>>() {});
        log.info("Loaded {} golden Q&A items from {}", cachedItems.size(), datasetResource.getFilename());
    }

    @Override
    public List<GoldenQaItem> loadAll() {
        return cachedItems;
    }
}
