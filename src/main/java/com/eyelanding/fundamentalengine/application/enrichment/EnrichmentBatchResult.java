package com.eyelanding.fundamentalengine.application.enrichment;

/**
 * Aggregate result of an enrichment batch run.
 */
public record EnrichmentBatchResult(
    Long batchId,
    String status,
    int totalTickers,
    int successTickers,
    int failedTickers,
    int skippedTickers,
    int totalMetricsCreated,
    long durationMs
) {}
