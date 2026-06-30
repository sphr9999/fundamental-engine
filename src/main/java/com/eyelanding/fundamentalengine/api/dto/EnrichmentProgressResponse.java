package com.eyelanding.fundamentalengine.api.dto;

/**
 * API response DTO for enrichment batch progress and result.
 */
public record EnrichmentProgressResponse(
    Long batchId,
    String status,
    int totalTickers,
    int successTickers,
    int failedTickers,
    int skippedTickers,
    int totalMetricsCreated,
    long durationMs,
    String reportPeriod
) {}
