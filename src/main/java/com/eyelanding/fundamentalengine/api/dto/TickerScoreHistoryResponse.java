package com.eyelanding.fundamentalengine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FA score history for a ticker across all periods and batches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerScoreHistoryResponse {
    private String ticker;
    private List<ScorePoint> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorePoint {
        private String period;
        private BigDecimal overallScore;
        private BigDecimal growthScore;
        private BigDecimal profitabilityScore;
        private BigDecimal valuationScore;
        private BigDecimal stabilityScore;
        private BigDecimal dataQualityScore;
        private String rating;
        private String explanation;
        private Long batchId;
        private LocalDateTime calculatedAt;
    }
}
