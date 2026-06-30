package com.eyelanding.fundamentalengine.api.controller;

import com.eyelanding.fundamentalengine.api.dto.ImportBatchResponse;
import com.eyelanding.fundamentalengine.api.dto.ImportPreviewResponse;
import com.eyelanding.fundamentalengine.api.dto.QualityReportResponse;
import com.eyelanding.fundamentalengine.application.importbatch.ExcelImportOrchestrator;
import com.eyelanding.fundamentalengine.application.importbatch.ImportBatchQueryService;
import com.eyelanding.fundamentalengine.application.importbatch.ImportPreviewService;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaImportBatchEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/internal/fa/import-batches")
@RequiredArgsConstructor
@Tag(name = "FA Import", description = "Excel workbook import for fundamental analysis data")
public class FaImportController {

    private final ExcelImportOrchestrator orchestrator;
    private final ImportBatchQueryService queryService;
    private final ImportPreviewService previewService;

    /**
     * POST /internal/fa/import-batches/excel/preview
     * Validate workbook and return sheet analysis without committing data.
     */
    @PostMapping(value = "/excel/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview Excel import", description = "Validate workbook before committing — no data saved")
    public ResponseEntity<ImportPreviewResponse> previewExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "reportPeriod", required = false) String reportPeriod) {
        try {
            ImportPreviewResponse preview = previewService.preview(
                    file.getInputStream(), file.getOriginalFilename(), reportPeriod);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Preview failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /internal/fa/import-batches/excel
     * Upload and import an Excel workbook.
     */
    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import Excel workbook", description = "Upload .xlsx file to import FA data")
    public ResponseEntity<ImportBatchResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "reportPeriod", required = false) String reportPeriod,
            @RequestParam(value = "importedBy", required = false) String importedBy) {

        log.info("Excel import request: file={}, size={}, period={}, by={}",
                file.getOriginalFilename(), file.getSize(), reportPeriod, importedBy);

        try {
            FaImportBatchEntity batch = orchestrator.importExcel(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    reportPeriod,
                    importedBy);

            ImportBatchResponse response = queryService.getById(batch.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /internal/fa/import-batches/{batchId}
     */
    @GetMapping("/{batchId}")
    @Operation(summary = "Get import batch status")
    public ResponseEntity<ImportBatchResponse> getBatch(@PathVariable Long batchId) {
        try {
            return ResponseEntity.ok(queryService.getById(batchId));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /internal/fa/import-batches/{batchId}/quality-report
     */
    @GetMapping("/{batchId}/quality-report")
    @Operation(summary = "Get quality report for an import batch")
    public ResponseEntity<QualityReportResponse> getQualityReport(
            @PathVariable Long batchId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        try {
            return ResponseEntity.ok(queryService.getQualityReport(batchId, severity, limit));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
