package com.eyelanding.fundamentalengine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated ratios for a ticker (YoY, QoQ, margins, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerRatiosResponse {
    private String ticker;
    private String period;
    private Long batchId;
    private List<RatioItem> ratios;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatioItem {
        private String ratioCode;
        private BigDecimal value;
        private String quality;
    }
}
