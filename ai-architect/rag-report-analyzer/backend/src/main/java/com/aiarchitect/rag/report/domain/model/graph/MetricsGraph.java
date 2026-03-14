package com.aiarchitect.rag.report.domain.model.graph;

import java.util.List;

/**
 * D3-compatible force-directed graph of financial metrics for a company.
 *
 * <p>Graph shape: {@code Company → Report → Metric}
 * <ul>
 *   <li>One "company" node per ticker
 *   <li>One "report" node per (year, quarter)
 *   <li>One "metric" node per non-null metric value per report
 * </ul>
 */
public record MetricsGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
}
