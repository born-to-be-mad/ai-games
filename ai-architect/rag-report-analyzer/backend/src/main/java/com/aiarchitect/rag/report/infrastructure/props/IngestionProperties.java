package com.aiarchitect.rag.report.infrastructure.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the startup ingestion runner.
 *
 * <p>Bound to {@code app.ingestion.*} in {@code application.yml}.
 *
 * <table>
 *   <tr><th>Property</th><th>Env var</th><th>Default</th></tr>
 *   <tr><td>app.ingestion.run-on-start</td><td>INGEST_ON_START</td><td>false</td></tr>
 *   <tr><td>app.ingestion.pdf-path</td><td>SAMPLE_PDF_PATH</td><td>../tmp/NVIDIA-2025-Annual-Report.pdf</td></tr>
 *   <tr><td>app.ingestion.ticker</td><td>INGEST_TICKER</td><td>NVDA</td></tr>
 *   <tr><td>app.ingestion.year</td><td>INGEST_YEAR</td><td>2025</td></tr>
 *   <tr><td>app.ingestion.quarter</td><td>INGEST_QUARTER</td><td>Annual</td></tr>
 * </table>
 */
@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        boolean runOnStart,
        String pdfPath,
        String ticker,
        int year,
        String quarter
) {

    public IngestionProperties {
        if (pdfPath == null || pdfPath.isBlank()) {
            pdfPath = "../tmp/NVIDIA-2025-Annual-Report.pdf";
        }
        if (ticker == null || ticker.isBlank()) {
            ticker = "NVDA";
        }
        if (year == 0) {
            year = 2025;
        }
        if (quarter == null || quarter.isBlank()) {
            quarter = "Annual";
        }
    }
}
