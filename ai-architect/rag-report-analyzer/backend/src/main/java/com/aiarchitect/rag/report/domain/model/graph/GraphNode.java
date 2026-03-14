package com.aiarchitect.rag.report.domain.model.graph;

import java.util.Map;

/**
 * A node in the financial knowledge graph (D3-compatible).
 *
 * @param id   unique node identifier
 * @param type node type: "company", "report", or "metric"
 * @param data additional display data (name, value, unit, etc.)
 */
public record GraphNode(String id, String type, Map<String, Object> data) {
}
