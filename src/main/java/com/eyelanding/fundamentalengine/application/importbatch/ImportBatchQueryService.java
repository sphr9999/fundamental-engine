package com.eyelanding.fundamentalengine.application.importbatch;

import com.eyelanding.fundamentalengine.api.dto.ImportBatchResponse;
import com.eyelanding.fundamentalengine.api.dto.QualityReportResponse;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaDataQualityIssueEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaImportBatchEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaDataQualityIssueRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ImportBatchQueryService {

    private final FaImportBatchRepository batchRepo;
    private final FaDataQualityIssueRepository qualityIssueRepo;

    public ImportBatchResponse getById(Long batchId) {
        var entity = batchRepo.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Import batch not found: " + batchId));
        return toResponse(entity);
    }

    public QualityReportResponse getQualityReport(Long batchId, String severity, int limit) {
        batchRepo.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Import batch not found: " + batchId));

        List<FaDataQualityIssueEntity> issues = severity != null
                ? qualityIssueRepo.findByImportBatchIdAndSeverityOrderByCreatedAtAsc(batchId, severity.toUpperCase())
                : qualityIssueRepo.findByImportBatchIdOrderByCreatedAtAsc(batchId);

        // Summary count by severity
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("ERROR", qualityIssueRepo.countByImportBatchIdAndSeverity(batchId, "ERROR"));
        summary.put("WARN", qualityIssueRepo.countByImportBatchIdAndSeverity(batchId, "WARN"));
        summary.put("INFO", qualityIssueRepo.countByImportBatchIdAndSeverity(batchId, "INFO"));

        var issueItems = issues.stream()
                .limit(limit)
                .map(i -> QualityReportResponse.QualityIssueItem.builder()
                        .ticker(i.getTicker())
                        .period(i.getPeriodCode())
                        .metric(i.getMetricCode())
                        .issueType(i.getIssueType())
                        .severity(i.getSeverity())
                        .sourceSheet(i.getSourceSheet())
                        .sourceCell(i.getSourceCell())
                        .message(i.getMessage())
                        .build())
                .toList();

        return QualityReportResponse.builder()
                .batchId(batchId)
                .summary(summary)
                .issues(issueItems)
                .build();
    }

    private ImportBatchResponse toResponse(FaImportBatchEntity e) {
        return ImportBatchResponse.builder()
                .batchId(e.getId())
                .status(e.getStatus())
                .sourceFileName(e.getSourceFileName())
                .sourceFileChecksum(e.getSourceFileChecksum())
                .reportPeriod(e.getReportPeriod())
                .totalRows(e.getTotalRows())
                .successRows(e.getSuccessRows())
                .warningRows(e.getWarningRows())
                .errorRows(e.getErrorRows())
                .importedBy(e.getImportedBy())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .errorMessage(e.getErrorMessage())
                .build();
    }
}
