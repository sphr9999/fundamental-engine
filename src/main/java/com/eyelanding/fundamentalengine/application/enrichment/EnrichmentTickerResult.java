package com.eyelanding.fundamentalengine.application.enrichment;

/**
 * Result of enriching a single ticker.
 */
public record EnrichmentTickerResult(
    String ticker,
    int metricsCreated,
    int qualityIssues,
    String status   // OK, PARTIAL, FAILED, SKIPPED
) {
    public static EnrichmentTickerResult ok(String ticker, int metrics, int issues) {
        return new EnrichmentTickerResult(ticker, metrics, issues, issues > 0 ? "PARTIAL" : "OK");
    }

    public static EnrichmentTickerResult failed(String ticker, String reason) {
        return new EnrichmentTickerResult(ticker, 0, 0, "FAILED");
    }

    public static EnrichmentTickerResult skipped(String ticker) {
        return new EnrichmentTickerResult(ticker, 0, 0, "SKIPPED");
    }
}
