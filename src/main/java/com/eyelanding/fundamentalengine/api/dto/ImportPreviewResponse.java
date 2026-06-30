package com.eyelanding.fundamentalengine.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response for import preview — shows what would be imported without committing.
 */
@Data
@Builder
public class ImportPreviewResponse {
    private String fileName;
    private String checksum;
    private String reportPeriod;
    private int totalSheets;
    private List<SheetPreview> sheets;
    private List<String> warnings;
    private boolean safe; // true = ok to commit, false = has critical issues

    @Data
    @Builder
    public static class SheetPreview {
        private String sheetName;
        private String logicalSheet;   // REVENUE, NPAT, FILTER, etc.
        private boolean recognized;
        private int estimatedRows;
        private Map<String, Integer> sampleTickers; // ticker → row count
        private List<String> issues;
    }
}
