package com.eyelanding.fundamentalengine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Industry benchmark response: median FA ratios grouped by industry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryBenchmarkResponse {
    private String period;
    private int industryCount;
    private List<IndustryBenchmark> benchmarks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryBenchmark {
        private String industry;
        private int tickerCount;
        private BigDecimal medianPe;
        private BigDecimal medianPb;
        private BigDecimal medianNetMargin;
        private BigDecimal medianGrossMargin;
        private BigDecimal medianRevenueYoy;
        private BigDecimal medianNpatYoy;
        private BigDecimal medianFaScore;
    }
}
