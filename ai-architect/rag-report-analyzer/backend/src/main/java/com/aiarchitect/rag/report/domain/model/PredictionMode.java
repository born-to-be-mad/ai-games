package com.aiarchitect.rag.report.domain.model;

/**
 * Selects the active prediction algorithm for {@link com.aiarchitect.rag.report.domain.port.in.PredictionFacade}.
 *
 * <ul>
 *   <li>{@link #NARRATIVE_PREDICTION} — qualitative trend analysis via LLM
 *   <li>{@link #LINEAR_REGRESSION} — numeric extrapolation via Apache Commons Math
 *   <li>{@link #HYBRID} — linear regression numbers wrapped in an LLM narrative
 * </ul>
 */
public enum PredictionMode {
    NARRATIVE_PREDICTION,
    LINEAR_REGRESSION,
    HYBRID
}
