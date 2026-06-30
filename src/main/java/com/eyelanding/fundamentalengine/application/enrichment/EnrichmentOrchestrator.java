package com.eyelanding.fundamentalengine.application.enrichment;

import com.eyelanding.fundamentalengine.application.ratio.RatioCalculationService;
import com.eyelanding.fundamentalengine.application.score.FaScoreCalculationService;
import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.PeriodType;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaCompanyEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaDataQualityIssueEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialMetricEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaImportBatchEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaCompanyRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaDataQualityIssueRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaFinancialMetricRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaImportBatchRepository;
import com.eyelanding.fundamentalengine.infrastructure.vci.VciHttpClient;
import com.eyelanding.fundamentalengine.infrastructure.vci.dto.VciFinancialItem;
import com.eyelanding.fundamentalengine.infrastructure.vci.mapping.VciFieldMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates VCI API data enrichment for financial metrics.
 *
 * <p>This service coordinates fetching financial statements from the VCI API,
 * parsing the response into normalized metrics, persisting them, and triggering
 * ratio/score calculations on the resulting batch.</p>
 *
 * <p>Each ticker is saved in its own transaction via {@link #enrichTickerTransactional},
 * so partial progress is committed immediately — no risk of losing 30 minutes
 * of work if one ticker fails near the end.</p>
 *
 * <p>The batch status is updated periodically during enrichment so that
 * polling {@code GET /batches/{batchId}} shows real-time progress.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentOrchestrator {

    private final VciHttpClient vciHttpClient;
    private final FaCompanyRepository companyRepository;
    private final FaFinancialMetricRepository metricRepository;
    private final FaImportBatchRepository batchRepository;
    private final FaDataQualityIssueRepository qualityIssueRepository;
    private final RatioCalculationService ratioCalculationService;
    private final FaScoreCalculationService faScoreCalculationService;

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Creates an import batch for tracking. Called before async execution starts.
     *
     * @return the batch ID
     */
    @Transactional
    public Long createBatch(EnrichmentRequest request) {
        List<String> tickers = resolveTickerList(request);

        FaImportBatchEntity batch = FaImportBatchEntity.builder()
                .sourceType("VCI_API")
                .sourceFileName("VCI API Enrichment")
                .sourceFileChecksum("vci-api-" + System.currentTimeMillis())
                .status(ImportStatus.PROCESSING)
                .importedBy(request.importedBy())
                .totalRows(tickers.size())
                .successRows(0)
                .warningRows(0)
                .errorRows(0)
                .startedAt(LocalDateTime.now())
                .build();
        batch = batchRepository.save(batch);

        log.info("Enrichment batch {} created — {} tickers to process", batch.getId(), tickers.size());
        return batch.getId();
    }

    /**
     * Runs enrichment for all tickers. Designed to be called from a background thread.
     *
     * <p>NOT @Transactional — each ticker commits independently via
     * {@link #enrichTickerTransactional}.</p>
     */
    public EnrichmentBatchResult enrichAll(Long batchId, EnrichmentRequest request) {
        long startTime = System.currentTimeMillis();

        // Determine ticker list
        List<String> tickers = resolveTickerList(request);
        log.info("Enrichment batch {} — processing {} tickers", batchId, tickers.size());

        if (tickers.isEmpty()) {
            updateBatchStatus(batchId, ImportStatus.SUCCESS, null, 0, 0, 0, 0);
            return new EnrichmentBatchResult(batchId, "SUCCESS", 0, 0, 0, 0, 0,
                    System.currentTimeMillis() - startTime);
        }

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int totalMetrics = 0;
        String detectedReportPeriod = null;

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);
            try {
                // Each ticker commits in its own transaction
                EnrichmentTickerResult result = enrichTickerTransactional(ticker, batchId, request);

                switch (result.status()) {
                    case "OK", "PARTIAL" -> {
                        successCount++;
                        totalMetrics += result.metricsCreated();
                    }
                    case "FAILED" -> failedCount++;
                    case "SKIPPED" -> skippedCount++;
                }

                // Detect report period from first successful ticker
                if (detectedReportPeriod == null && result.metricsCreated() > 0) {
                    detectedReportPeriod = detectLatestPeriod(batchId, ticker);
                }

            } catch (Exception e) {
                log.error("Unexpected error enriching ticker {}: {}", ticker, e.getMessage(), e);
                failedCount++;
                saveQualityIssueTransactional(batchId, ticker, null, null,
                        "ENRICHMENT_ERROR", "ERROR",
                        "VCI_API", "Unexpected error: " + e.getMessage());
            }

            // Update batch progress every 50 tickers
            if ((i + 1) % 50 == 0 || i == tickers.size() - 1) {
                updateBatchProgress(batchId, successCount, skippedCount, failedCount);
                log.info("Enrichment batch {} progress: {}/{} tickers (success={}, failed={}, skipped={})",
                        batchId, i + 1, tickers.size(), successCount, failedCount, skippedCount);
            }
        }

        // Run ratio and score calculations
        if (detectedReportPeriod != null && successCount > 0) {
            try {
                log.info("Enrichment batch {} — calculating ratios for period {}",
                        batchId, detectedReportPeriod);
                ratioCalculationService.calculateForBatch(batchId, detectedReportPeriod);
            } catch (Exception e) {
                log.error("Ratio calculation failed for batch {}: {}", batchId, e.getMessage(), e);
            }

            try {
                log.info("Enrichment batch {} — calculating FA scores for period {}",
                        batchId, detectedReportPeriod);
                faScoreCalculationService.calculateForBatch(batchId, detectedReportPeriod);
            } catch (Exception e) {
                log.error("Score calculation failed for batch {}: {}", batchId, e.getMessage(), e);
            }
        }

        // Final batch status
        ImportStatus finalStatus = determineFinalStatus(successCount, failedCount, tickers.size());
        updateBatchStatus(batchId, finalStatus, detectedReportPeriod,
                tickers.size(), successCount, skippedCount, failedCount);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Enrichment batch {} completed — status={}, tickers={}, success={}, "
                        + "failed={}, skipped={}, metrics={}, duration={}ms",
                batchId, finalStatus, tickers.size(), successCount,
                failedCount, skippedCount, totalMetrics, durationMs);

        return new EnrichmentBatchResult(
                batchId, finalStatus.name(), tickers.size(),
                successCount, failedCount, skippedCount, totalMetrics, durationMs);
    }

    // ── Per-ticker transactional methods ─────────────────────────────────

    /**
     * Enriches a single ticker in its own transaction.
     * If this fails, only this ticker's data is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnrichmentTickerResult enrichTickerTransactional(String ticker, Long batchId,
                                                            EnrichmentRequest request) {
        return enrichTicker(ticker, batchId, request);
    }

    /**
     * Saves a quality issue in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQualityIssueTransactional(Long batchId, String ticker, String periodCode,
                                               String metricCode, String issueType, String severity,
                                               String sourceSheet, String message) {
        saveQualityIssue(batchId, ticker, periodCode, metricCode, issueType, severity, sourceSheet, message);
    }

    // ── Private: ticker enrichment ──────────────────────────────────────

    private EnrichmentTickerResult enrichTicker(String ticker, Long batchId,
                                                EnrichmentRequest request) {
        int metricsCreated = 0;
        int qualityIssues = 0;
        String periodParam = "quarter".equalsIgnoreCase(request.effectivePeriod()) ? "Q" : "Y";

        for (String reportType : request.effectiveReportTypes()) {
            try {
                Optional<List<VciFinancialItem>> itemsOpt =
                        vciHttpClient.fetchFinancialStatement(ticker, reportType, periodParam);

                if (itemsOpt.isEmpty() || itemsOpt.get().isEmpty()) {
                    log.debug("No data from VCI for ticker={}, reportType={}", ticker, reportType);
                    qualityIssues++;
                    saveQualityIssue(batchId, ticker, null, null,
                            "NO_DATA", "WARN",
                            "VCI_API:" + reportType,
                            "No data returned from VCI API for " + reportType);
                    continue;
                }

                List<VciFinancialItem> items = itemsOpt.get();
                List<FaFinancialMetricEntity> metricsToSave = new ArrayList<>();

                for (VciFinancialItem item : items) {
                    // Resolve MetricCode via static VciFieldMapping (e.g., isa3 → REVENUE)
                    Optional<MetricCode> metricCodeOpt =
                            VciFieldMapping.resolve(item.getFieldName(), reportType);

                    if (metricCodeOpt.isEmpty()) {
                        continue;
                    }

                    MetricCode metricCode = metricCodeOpt.get();

                    // VciResponseParser already returns normalized period codes: "2026Q1", "2025"
                    for (Map.Entry<String, BigDecimal> entry : item.getPeriodValues().entrySet()) {
                        String periodCode = entry.getKey();
                        BigDecimal value = entry.getValue();

                        PeriodType periodType = periodCode.contains("Q")
                                ? PeriodType.QUARTER : PeriodType.YEAR;

                        QualityStatus quality = (value != null) ? QualityStatus.OK : QualityStatus.MISSING;
                        String unit = resolveUnit(metricCode);

                        FaFinancialMetricEntity metric = FaFinancialMetricEntity.builder()
                                .ticker(ticker)
                                .periodType(periodType)
                                .periodCode(periodCode)
                                .metricCode(metricCode)
                                .metricValue(value)
                                .unit(unit)
                                .currency("VND")
                                .qualityStatus(quality)
                                .qualityNote(value == null ? "Null value from VCI API" : null)
                                .sourceSheet("VCI_API:" + reportType)
                                .importBatchId(batchId)
                                .build();

                        metricsToSave.add(metric);
                    }
                }

                if (!metricsToSave.isEmpty()) {
                    metricRepository.saveAll(metricsToSave);
                    metricsCreated += metricsToSave.size();
                    log.debug("Saved {} metrics for ticker={}, reportType={}",
                            metricsToSave.size(), ticker, reportType);
                }

            } catch (Exception e) {
                log.error("Error enriching ticker={} reportType={}: {}",
                        ticker, reportType, e.getMessage(), e);
                qualityIssues++;
                saveQualityIssue(batchId, ticker, null, null,
                        "FETCH_ERROR", "ERROR",
                        "VCI_API:" + reportType,
                        "Error fetching " + reportType + ": " + e.getMessage());
            }
        }

        return EnrichmentTickerResult.ok(ticker, metricsCreated, qualityIssues);
    }

    // ── Private: ticker resolution ──────────────────────────────────────

    private List<String> resolveTickerList(EnrichmentRequest request) {
        if (request.tickers() != null && !request.tickers().isEmpty()) {
            return request.tickers();
        }

        if (request.exchanges() != null && !request.exchanges().isEmpty()) {
            return companyRepository.findByExchangeInAndIsActiveTrue(request.exchanges())
                    .stream()
                    .map(FaCompanyEntity::getTicker)
                    .toList();
        }

        return companyRepository.findByIsActiveTrue()
                .stream()
                .map(FaCompanyEntity::getTicker)
                .toList();
    }

    private String resolveUnit(MetricCode metricCode) {
        return switch (metricCode) {
            case PB -> "RATIO";
            default -> "VND";
        };
    }

    // ── Private: batch updates ──────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatchProgress(Long batchId, int successRows, int warningRows, int errorRows) {
        batchRepository.findById(batchId).ifPresent(batch -> {
            batch.setSuccessRows(successRows);
            batch.setWarningRows(warningRows);
            batch.setErrorRows(errorRows);
            batchRepository.save(batch);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatchStatus(Long batchId, ImportStatus status, String reportPeriod,
                                   int totalRows, int successRows, int warningRows, int errorRows) {
        batchRepository.findById(batchId).ifPresent(batch -> {
            batch.setStatus(status);
            batch.setReportPeriod(reportPeriod);
            batch.setTotalRows(totalRows);
            batch.setSuccessRows(successRows);
            batch.setWarningRows(warningRows);
            batch.setErrorRows(errorRows);
            batch.setFinishedAt(LocalDateTime.now());
            batchRepository.save(batch);
        });
    }

    // ── Private: helpers ────────────────────────────────────────────────

    private String detectLatestPeriod(Long batchId, String ticker) {
        List<FaFinancialMetricEntity> metrics =
                metricRepository.findByTickerAndImportBatchId(ticker, batchId);

        return metrics.stream()
                .map(FaFinancialMetricEntity::getPeriodCode)
                .max(String::compareTo)
                .orElse(null);
    }

    private ImportStatus determineFinalStatus(int successCount, int failedCount, int totalTickers) {
        if (failedCount == 0 && successCount > 0) {
            return ImportStatus.SUCCESS;
        }
        if (successCount > 0) {
            return ImportStatus.PARTIAL_SUCCESS;
        }
        return ImportStatus.FAILED;
    }

    private void saveQualityIssue(Long batchId, String ticker, String periodCode,
                                  String metricCode, String issueType, String severity,
                                  String sourceSheet, String message) {
        FaDataQualityIssueEntity issue = FaDataQualityIssueEntity.builder()
                .importBatchId(batchId)
                .ticker(ticker)
                .periodCode(periodCode)
                .metricCode(metricCode)
                .issueType(issueType)
                .severity(severity)
                .sourceSheet(sourceSheet)
                .message(message)
                .build();
        qualityIssueRepository.save(issue);
    }
}
