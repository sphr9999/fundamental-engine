package com.eyelanding.fundamentalengine.application.importbatch;

import com.eyelanding.fundamentalengine.application.ratio.RatioCalculationService;
import com.eyelanding.fundamentalengine.application.score.FaScoreCalculationService;
import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.PeriodType;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.infrastructure.config.RedisCacheConfig;
import com.eyelanding.fundamentalengine.infrastructure.excel.*;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.*;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates the full Excel import pipeline:
 * 1. Compute checksum
 * 2. Create import batch (PROCESSING)
 * 3. Parse each sheet → extract metrics
 * 4. Save metrics + quality issues
 * 5. Update batch (SUCCESS/PARTIAL_SUCCESS/FAILED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportOrchestrator {

    private static final String IMPORT_SOURCE_TYPE = "EXCEL_WORKBOOK";
    private static final String REPORT_PERIOD_DEFAULT = "2026Q1";

    private final FaImportBatchRepository batchRepo;
    private final FaCompanyRepository companyRepo;
    private final FaFinancialMetricRepository metricRepo;
    private final FaDataQualityIssueRepository qualityIssueRepo;

    private final ExcelSheetAliasResolver sheetAliasResolver;
    private final FinancialSheetParser financialSheetParser;
    private final PointInTimeSheetParser pointInTimeSheetParser;
    private final FilterSheetParser filterSheetParser;
    private final RatioCalculationService ratioCalculationService;
    private final FaScoreCalculationService scoreCalculationService;

    /**
     * Main entry point for Excel file import.
     */
    @Transactional
    @CacheEvict(cacheNames = {
            RedisCacheConfig.CACHE_TICKER_OVERVIEW,
            RedisCacheConfig.CACHE_TICKER_RATIOS,
            RedisCacheConfig.CACHE_SCREENER,
            RedisCacheConfig.CACHE_INDUSTRY_BENCHMARK
    }, allEntries = true)
    public FaImportBatchEntity importExcel(InputStream inputStream, String originalFileName,
                                            String reportPeriod, String importedBy) throws Exception {
        // Step 1: Read bytes and compute checksum simultaneously
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes;
        try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
            fileBytes = dis.readAllBytes();
        }
        String checksum = HexFormat.of().formatHex(digest.digest());

        // Step 2: Create batch record
        String period = (reportPeriod != null && !reportPeriod.isBlank()) ? reportPeriod : REPORT_PERIOD_DEFAULT;
        FaImportBatchEntity batch = FaImportBatchEntity.builder()
                .sourceType(IMPORT_SOURCE_TYPE)
                .sourceFileName(originalFileName)
                .sourceFileChecksum(checksum)
                .reportPeriod(period)
                .status(ImportStatus.PROCESSING)
                .importedBy(importedBy)
                .startedAt(LocalDateTime.now())
                .build();
        batch = batchRepo.save(batch);
        Long batchId = batch.getId();
        log.info("Import batch {} created for file: {}", batchId, originalFileName);

        // Step 3: Parse workbook
        int totalRows = 0;
        int successRows = 0;
        int warningRows = 0;
        int errorRows = 0;
        List<FaDataQualityIssueEntity> qualityIssues = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(fileBytes))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.setIgnoreMissingWorkbooks(true);

            // Step 4: Process each sheet
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                String sheetName = workbook.getSheetName(si);
                LogicalSheet logical = sheetAliasResolver.resolve(sheetName);

                log.info("Processing sheet [{}] → {}", sheetName, logical);

                switch (logical) {
                    case REVENUE -> {
                        var rows = financialSheetParser.parse(sheet, MetricCode.REVENUE, PeriodType.QUARTER, evaluator);
                        var stats = saveMetricRows(rows, MetricCode.REVENUE, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                        updateCompanies(rows);
                    }
                    case NPAT -> {
                        var rows = financialSheetParser.parse(sheet, MetricCode.NPAT, PeriodType.QUARTER, evaluator);
                        var stats = saveMetricRows(rows, MetricCode.NPAT, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case GROSS_PROFIT -> {
                        var rows = financialSheetParser.parse(sheet, MetricCode.GROSS_PROFIT, PeriodType.QUARTER, evaluator);
                        var stats = saveMetricRows(rows, MetricCode.GROSS_PROFIT, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case NPAT_YEARLY -> {
                        var rows = financialSheetParser.parse(sheet, MetricCode.NPAT_YEARLY, PeriodType.YEAR, evaluator);
                        var stats = saveMetricRows(rows, MetricCode.NPAT_YEARLY, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case EPS_DILUTED -> {
                        var rows = pointInTimeSheetParser.parse(sheet, evaluator);
                        var stats = savePointInTimeRows(rows, MetricCode.EPS_DILUTED, PeriodType.TTM, period, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case SHARES_OUTSTANDING -> {
                        var rows = pointInTimeSheetParser.parse(sheet, evaluator);
                        var stats = savePointInTimeRows(rows, MetricCode.SHARES_OUTSTANDING, PeriodType.POINT_IN_TIME, period, batchId, "SHARE", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case STOCK_PRICE -> {
                        var rows = pointInTimeSheetParser.parse(sheet, evaluator);
                        var stats = savePointInTimeRows(rows, MetricCode.CLOSE_PRICE, PeriodType.POINT_IN_TIME, period, batchId, "VND", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case PB -> {
                        var rows = pointInTimeSheetParser.parse(sheet, evaluator);
                        var stats = savePointInTimeRows(rows, MetricCode.PB, PeriodType.POINT_IN_TIME, period, batchId, "RATIO", qualityIssues);
                        totalRows += stats[0]; successRows += stats[1];
                        warningRows += stats[2]; errorRows += stats[3];
                    }
                    case FILTER -> {
                        // Lọc sheet: primary source for industry/sector classification
                        var companies = filterSheetParser.parse(sheet, evaluator);
                        updateCompaniesFromFilter(companies);
                        log.info("Updated {} companies from FILTER sheet", companies.size());
                    }
                    default -> log.debug("Skipping sheet [{}]", sheetName);
                }
            }
        } catch (Exception e) {
            log.error("Import batch {} failed during parsing: {}", batchId, e.getMessage(), e);
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorMessage(e.getMessage());
            batch.setFinishedAt(LocalDateTime.now());
            batchRepo.save(batch);
            throw e;
        }

        // Step 5: Save quality issues in batch
        if (!qualityIssues.isEmpty()) {
            qualityIssueRepo.saveAll(qualityIssues);
        }

        // Step 6: Finalize batch
        batch.setTotalRows(totalRows);
        batch.setSuccessRows(successRows);
        batch.setWarningRows(warningRows);
        batch.setErrorRows(errorRows);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setStatus(errorRows == 0 ? ImportStatus.SUCCESS : ImportStatus.PARTIAL_SUCCESS);
        batch = batchRepo.save(batch);

        log.info("Import batch {} completed: status={}, total={}, success={}, warn={}, error={}",
                batchId, batch.getStatus(), totalRows, successRows, warningRows, errorRows);

        // Step 7: Calculate ratios
        try {
            ratioCalculationService.calculateForBatch(batchId, period);
        } catch (Exception e) {
            log.warn("Ratio calculation failed for batch {}: {}", batchId, e.getMessage());
        }

        // Step 8: Calculate FA scores
        try {
            scoreCalculationService.calculateForBatch(batchId, period);
        } catch (Exception e) {
            log.warn("Score calculation failed for batch {}: {}", batchId, e.getMessage());
        }

        return batch;
    }

    private int[] saveMetricRows(List<FinancialSheetParser.MetricRow> rows,
                                   MetricCode metricCode, Long batchId, String unit,
                                   List<FaDataQualityIssueEntity> issues) {
        int total = 0, success = 0, warn = 0, error = 0;
        List<FaFinancialMetricEntity> toSave = new ArrayList<>();
        // Dedup key: ticker|periodCode|metricCode — prevent duplicate rows in same sheet
        Set<String> seen = new java.util.HashSet<>();

        for (var row : rows) {
            total++;
            String dedupKey = row.ticker() + "|" + row.periodCode() + "|" + metricCode.name();
            if (!seen.add(dedupKey)) {
                log.debug("Skipping duplicate row: {}", dedupKey);
                warn++;
                continue;
            }

            var result = row.value();
            QualityStatus qs = result.quality();

            FaFinancialMetricEntity entity = FaFinancialMetricEntity.builder()
                    .ticker(row.ticker())
                    .periodType(row.periodType())
                    .periodCode(row.periodCode())
                    .metricCode(metricCode)
                    .metricValue(result.numericValue())
                    .unit(unit)
                    .currency(unit.equals("VND") ? "VND" : null)
                    .qualityStatus(qs)
                    .qualityNote(result.qualityNote())
                    .sourceSheet(row.sourceSheet())
                    .sourceCell(row.sourceCell())
                    .importBatchId(batchId)
                    .build();

            toSave.add(entity);

            if (qs == QualityStatus.OK) success++;
            else if (qs == QualityStatus.FORMULA_ERROR || qs == QualityStatus.SUSPICIOUS) {
                error++;
                issues.add(buildIssue(batchId, row.ticker(), row.periodCode(),
                        metricCode.name(), qs.name(), "ERROR", row.sourceSheet(), row.sourceCell(),
                        result.qualityNote()));
            } else {
                warn++;
                issues.add(buildIssue(batchId, row.ticker(), row.periodCode(),
                        metricCode.name(), qs.name(), "WARN", row.sourceSheet(), row.sourceCell(),
                        result.qualityNote()));
            }
        }

        if (!toSave.isEmpty()) {
            metricRepo.saveAll(toSave);
        }
        return new int[]{total, success, warn, error};
    }

    private int[] savePointInTimeRows(List<PointInTimeSheetParser.CompanyPointRow> rows,
                                       MetricCode metricCode, PeriodType periodType,
                                       String periodCode, Long batchId, String unit,
                                       List<FaDataQualityIssueEntity> issues) {
        int total = 0, success = 0, warn = 0, error = 0;
        List<FaFinancialMetricEntity> toSave = new ArrayList<>();
        // Dedup: ticker|metricCode — point-in-time has only 1 period so no period in key
        Set<String> seen = new java.util.HashSet<>();

        for (var row : rows) {
            total++;
            String dedupKey = row.ticker() + "|" + metricCode.name();
            if (!seen.add(dedupKey)) {
                log.debug("Skipping duplicate point-in-time row: {}", dedupKey);
                warn++;
                continue;
            }

            var result = row.value();
            QualityStatus qs = result.quality();

            FaFinancialMetricEntity entity = FaFinancialMetricEntity.builder()
                    .ticker(row.ticker())
                    .periodType(periodType)
                    .periodCode(periodCode)
                    .metricCode(metricCode)
                    .metricValue(result.numericValue())
                    .unit(unit)
                    .currency(unit.equals("VND") ? "VND" : null)
                    .qualityStatus(qs)
                    .qualityNote(result.qualityNote())
                    .sourceSheet(row.sourceSheet())
                    .sourceCell(row.sourceCell())
                    .importBatchId(batchId)
                    .build();
            toSave.add(entity);

            if (qs == QualityStatus.OK) success++;
            else if (qs == QualityStatus.FORMULA_ERROR || qs == QualityStatus.SUSPICIOUS) {
                error++;
                issues.add(buildIssue(batchId, row.ticker(), periodCode,
                        metricCode.name(), qs.name(), "ERROR", row.sourceSheet(), row.sourceCell(),
                        result.qualityNote()));
            } else {
                warn++;
                issues.add(buildIssue(batchId, row.ticker(), periodCode,
                        metricCode.name(), qs.name(), "WARN", row.sourceSheet(), row.sourceCell(),
                        result.qualityNote()));
            }
        }

        if (!toSave.isEmpty()) {
            metricRepo.saveAll(toSave);
        }
        return new int[]{total, success, warn, error};
    }

    /**
     * Upsert companies from revenue sheet (first sheet processed).
     */
    private void updateCompanies(List<FinancialSheetParser.MetricRow> rows) {
        Map<String, FinancialSheetParser.MetricRow> unique = new LinkedHashMap<>();
        for (var row : rows) {
            unique.putIfAbsent(row.ticker(), row);
        }
        for (var entry : unique.entrySet()) {
            String ticker = entry.getKey();
            var row = entry.getValue();
            if (!companyRepo.existsByTicker(ticker)) {
                companyRepo.save(FaCompanyEntity.builder()
                        .ticker(ticker)
                        .companyName(row.companyName())
                        .exchange(row.exchange())
                        .build());
            }
        }
    }

    /**
     * Upsert companies from the Lọc (Filter) sheet — enriches with industry/sector data.
     * Creates company record if not exist, updates industry if already present.
     */
    private void updateCompaniesFromFilter(List<FilterSheetParser.CompanyInfo> companies) {
        for (var info : companies) {
            if (info.ticker() == null || info.ticker().isBlank()) continue;
            String ticker = info.ticker().toUpperCase();

            var existing = companyRepo.findByTicker(ticker);
            if (existing.isPresent()) {
                // Enrich with industry data
                var company = existing.get();
                if (info.industry() != null && !info.industry().isBlank()) {
                    company.setIndustryLevel1(info.industry());
                }
                if (info.companyName() != null && !info.companyName().isBlank()
                        && (company.getCompanyName() == null || company.getCompanyName().isBlank())) {
                    company.setCompanyName(info.companyName());
                }
                companyRepo.save(company);
            } else {
                // Create new company record
                companyRepo.save(FaCompanyEntity.builder()
                        .ticker(ticker)
                        .companyName(info.companyName())
                        .industryLevel1(info.industry())
                        .build());
            }
        }
    }

    private MetricCode guessMetricCode(FinancialSheetParser.MetricRow row) {
        // This shouldn't normally be called — caller should pass correct MetricCode
        // Added as fallback
        return MetricCode.REVENUE;
    }

    private FaDataQualityIssueEntity buildIssue(Long batchId, String ticker, String period,
                                                  String metricCode, String issueType,
                                                  String severity, String sheet, String cell,
                                                  String message) {
        return FaDataQualityIssueEntity.builder()
                .importBatchId(batchId)
                .ticker(ticker)
                .periodCode(period)
                .metricCode(metricCode)
                .issueType(issueType)
                .severity(severity)
                .sourceSheet(sheet)
                .sourceCell(cell)
                .message(message != null ? message : issueType)
                .build();
    }
}
