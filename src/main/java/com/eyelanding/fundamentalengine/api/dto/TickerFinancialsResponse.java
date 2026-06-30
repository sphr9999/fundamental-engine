package com.eyelanding.fundamentalengine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Financial metrics history for a ticker — quarterly or yearly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerFinancialsResponse {
    private String ticker;
    private String periodType; // QUARTER or YEAR
    private Long batchId;
    private List<MetricSeriesItem> series;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricSeriesItem {
        private String metricCode;
        private String unit;
        private List<PeriodValue> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodValue {
        private String period;
        private BigDecimal value;
        private String quality;  // OK, MISSING, FORMULA_ERROR, etc.
    }
}
