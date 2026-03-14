package com.aiarchitect.rag.report.domain.model.graph;

/**
 * A directed edge in the financial knowledge graph (D3-compatible).
 *
 * @param source source node id
 * @param target target node id
 * @param label  relationship label (e.g. "HAS_REPORT", "HAS_METRIC")
 */
public record GraphEdge(String source, String target, String label) {
}
