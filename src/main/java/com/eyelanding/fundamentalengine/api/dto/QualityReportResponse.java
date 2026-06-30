package com.eyelanding.fundamentalengine.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class QualityReportResponse {
    private Long batchId;
    private Map<String, Long> summary; // severity → count
    private List<QualityIssueItem> issues;

    @Data
    @Builder
    public static class QualityIssueItem {
        private String ticker;
        private String period;
        private String metric;
        private String issueType;
        private String severity;
        private String sourceSheet;
        private String sourceCell;
        private String message;
    }
}
