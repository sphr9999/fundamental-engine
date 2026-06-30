package com.eyelanding.fundamentalengine.api.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ticker overview response: combines company info, latest metrics, score, and highlights.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerOverviewResponse {
    private String ticker;
    private String companyName;
    private String exchange;
    private String industry;
    private String period;

    // Market data (point-in-time)
    private BigDecimal price;
    private BigDecimal pb;
    private BigDecimal peTtm;
    private BigDecimal marketCap;

    // FA Score
    private BigDecimal faScore;
    private String rating;
    private String dataQuality;

    // Score breakdown
    private BigDecimal growthScore;
    private BigDecimal profitabilityScore;
    private BigDecimal valuationScore;
    private BigDecimal stabilityScore;
    private BigDecimal dataQualityScore;

    // Narrative
    private List<String> highlights;
    private List<String> warnings;
}
