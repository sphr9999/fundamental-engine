package com.eyelanding.fundamentalengine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerResponse {
    private int page;
    private int pageSize;
    private long totalCount;
    private List<ScreenerItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenerItem {
        private String ticker;
        private String companyName;
        private String exchange;
        private String industry;
        private String rating;
        private BigDecimal overallScore;
        private BigDecimal growthScore;
        private BigDecimal profitabilityScore;
        private BigDecimal valuationScore;
        private String dataQuality;
    }
}
