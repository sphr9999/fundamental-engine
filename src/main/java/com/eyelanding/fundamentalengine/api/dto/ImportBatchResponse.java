package com.eyelanding.fundamentalengine.api.dto;

import com.eyelanding.fundamentalengine.domain.ImportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ImportBatchResponse {
    private Long batchId;
    private ImportStatus status;
    private String sourceFileName;
    private String sourceFileChecksum;
    private String reportPeriod;
    private Integer totalRows;
    private Integer successRows;
    private Integer warningRows;
    private Integer errorRows;
    private String importedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}
