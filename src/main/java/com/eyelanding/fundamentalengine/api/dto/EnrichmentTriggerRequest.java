package com.eyelanding.fundamentalengine.api.dto;

import java.util.List;

/**
 * API request DTO for triggering VCI data enrichment.
 */
public record EnrichmentTriggerRequest(
    List<String> tickers,
    List<String> exchanges,
    List<String> reportTypes,
    String period,
    String importedBy
) {}
