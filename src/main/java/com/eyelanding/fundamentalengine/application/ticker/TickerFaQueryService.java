package com.eyelanding.fundamentalengine.application.ticker;

import com.eyelanding.fundamentalengine.api.dto.TickerFinancialsResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerOverviewResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerRatiosResponse;
import com.eyelanding.fundamentalengine.api.dto.TickerScoreHistoryResponse;
import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.infrastructure.config.RedisCacheConfig;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaCompanyEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialMetricEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialRatioEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaScoreSnapshotEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TickerFaQueryService {

    private final FaCompanyRepository companyRepo;
    private final FaFinancialMetricRepository metricRepo;
    private final FaFinancialRatioRepository ratioRepo;
    private final FaScoreSnapshotRepository scoreRepo;
    private final FaImportBatchRepository batchRepo;

    @Cacheable(value = RedisCacheConfig.CACHE_TICKER_OVERVIEW, key = "#ticker.toUpperCase() + ':' + #period + ':' + #batchId")
    public TickerOverviewResponse getOverview(String ticker, String period, Long batchId) {
        String effectivePeriod = period != null ? period : "2026Q1";
        Long effectiveBatch = resolveLatestBatch(batchId);

        // Company info
        Optional<FaCompanyEntity> company = companyRepo.findByTicker(ticker.toUpperCase());

        // Metrics index for this ticker/period/batch
        Map<MetricCode, BigDecimal> metrics = getMetricValues(ticker.toUpperCase(), effectivePeriod, effectiveBatch);

        // Latest score
        Optional<FaScoreSnapshotEntity> score = scoreRepo
                .findTopByTickerAndPeriodCodeAndImportBatchIdOrderByCreatedAtDesc(
                        ticker.toUpperCase(), effectivePeriod, effectiveBatch);

        // Highlights & warnings
        List<String> highlights = buildHighlights(ticker, effectivePeriod, effectiveBatch);
        List<String> warnings = List.of("Cash flow data is not available in Phase 1");

        BigDecimal price = metrics.get(MetricCode.CLOSE_PRICE);
        BigDecimal shares = metrics.get(MetricCode.SHARES_OUTSTANDING);
        BigDecimal eps = metrics.get(MetricCode.EPS_DILUTED);
        BigDecimal pb = metrics.get(MetricCode.PB);
        BigDecimal marketCap = (price != null && shares != null)
                ? price.multiply(shares) : null;
        BigDecimal peTtm = (price != null && eps != null && eps.compareTo(BigDecimal.ZERO) != 0)
                ? price.divide(eps, 2, java.math.RoundingMode.HALF_UP) : null;

        return TickerOverviewResponse.builder()
                .ticker(ticker.toUpperCase())
                .companyName(company.map(FaCompanyEntity::getCompanyName).orElse(null))
                .exchange(company.map(FaCompanyEntity::getExchange).orElse(null))
                .industry(company.map(FaCompanyEntity::getIndustryLevel1).orElse(null))
                .period(effectivePeriod)
                .price(price)
                .pb(pb)
                .peTtm(peTtm)
                .marketCap(marketCap)
                .faScore(score.map(FaScoreSnapshotEntity::getOverallScore).orElse(null))
                .rating(score.map(FaScoreSnapshotEntity::getRating).orElse(null))
                .dataQuality(resolveDataQuality(ticker.toUpperCase(), effectivePeriod, effectiveBatch))
                .growthScore(score.map(FaScoreSnapshotEntity::getGrowthScore).orElse(null))
                .profitabilityScore(score.map(FaScoreSnapshotEntity::getProfitabilityScore).orElse(null))
                .valuationScore(score.map(FaScoreSnapshotEntity::getValuationScore).orElse(null))
                .stabilityScore(score.map(FaScoreSnapshotEntity::getStabilityScore).orElse(null))
                .dataQualityScore(score.map(FaScoreSnapshotEntity::getDataQualityScore).orElse(null))
                .highlights(highlights)
                .warnings(warnings)
                .build();
    }

    public TickerFinancialsResponse getFinancials(String ticker, String periodType, Long batchId) {
        Long effectiveBatch = resolveLatestBatch(batchId);
        String effectivePeriodType = periodType != null ? periodType : "QUARTER";

        List<FaFinancialMetricEntity> metrics = metricRepo.findByTickerAndImportBatchId(
                ticker.toUpperCase(), effectiveBatch);

        // Filter by period type
        List<FaFinancialMetricEntity> filtered = metrics.stream()
                .filter(m -> m.getPeriodType().name().equals(effectivePeriodType))
                .toList();

        // Group by metricCode → list of period values sorted chronologically
        Map<MetricCode, List<FaFinancialMetricEntity>> byMetric = filtered.stream()
                .collect(Collectors.groupingBy(FaFinancialMetricEntity::getMetricCode));

        List<TickerFinancialsResponse.MetricSeriesItem> series = new ArrayList<>();
        for (var entry : byMetric.entrySet()) {
            List<TickerFinancialsResponse.PeriodValue> values = entry.getValue().stream()
                    .sorted(Comparator.comparing(FaFinancialMetricEntity::getPeriodCode))
                    .map(m -> TickerFinancialsResponse.PeriodValue.builder()
                            .period(m.getPeriodCode())
                            .value(m.getMetricValue())
                            .quality(m.getQualityStatus().name())
                            .build())
                    .toList();

            series.add(TickerFinancialsResponse.MetricSeriesItem.builder()
                    .metricCode(entry.getKey().name())
                    .unit(entry.getValue().stream().findFirst().map(FaFinancialMetricEntity::getUnit).orElse(null))
                    .values(values)
                    .build());
        }

        return TickerFinancialsResponse.builder()
                .ticker(ticker.toUpperCase())
                .periodType(effectivePeriodType)
                .batchId(effectiveBatch)
                .series(series)
                .build();
    }

    public TickerRatiosResponse getRatios(String ticker, String period, Long batchId) {
        Long effectiveBatch = resolveLatestBatch(batchId);
        String effectivePeriod = period != null ? period : "2026Q1";

        List<FaFinancialRatioEntity> ratios = ratioRepo.findByTickerAndPeriodCodeAndImportBatchId(
                ticker.toUpperCase(), effectivePeriod, effectiveBatch);

        List<TickerRatiosResponse.RatioItem> items = ratios.stream()
                .map(r -> TickerRatiosResponse.RatioItem.builder()
                        .ratioCode(r.getRatioCode().name())
                        .value(r.getRatioValue())
                        .quality(r.getQualityStatus().name())
                        .build())
                .toList();

        return TickerRatiosResponse.builder()
                .ticker(ticker.toUpperCase())
                .period(effectivePeriod)
                .batchId(effectiveBatch)
                .ratios(items)
                .build();
    }

    // ─── Private helpers ─────────────────────────────────────────

    private Long resolveLatestBatch(Long batchId) {
        if (batchId != null) return batchId;
        return batchRepo.findTopByStatusOrderByCreatedAtDesc(
                com.eyelanding.fundamentalengine.domain.ImportStatus.SUCCESS)
                .map(b -> b.getId())
                .orElse(batchRepo.findAll().stream()
                        .mapToLong(b -> b.getId()).max().orElse(1L));
    }

    private Map<MetricCode, BigDecimal> getMetricValues(String ticker, String period, Long batchId) {
        List<FaFinancialMetricEntity> metrics = metricRepo.findByTickerAndPeriodCodeAndImportBatchId(
                ticker, period, batchId);
        Map<MetricCode, BigDecimal> result = new HashMap<>();
        for (var m : metrics) {
            if (m.getQualityStatus() == QualityStatus.OK && m.getMetricValue() != null) {
                result.put(m.getMetricCode(), m.getMetricValue());
            }
        }
        return result;
    }

    private List<String> buildHighlights(String ticker, String period, Long batchId) {
        List<String> highlights = new ArrayList<>();
        List<FaFinancialRatioEntity> ratios = ratioRepo.findByTickerAndPeriodCodeAndImportBatchId(
                ticker, period, batchId);

        for (var r : ratios) {
            if (r.getQualityStatus() != QualityStatus.OK || r.getRatioValue() == null) continue;
            double v = r.getRatioValue().doubleValue();
            switch (r.getRatioCode()) {
                case REVENUE_YOY -> {
                    if (v > 10) highlights.add(String.format("Revenue grew %.1f%% year-over-year", v));
                    else if (v < 0) highlights.add(String.format("Revenue declined %.1f%% year-over-year", Math.abs(v)));
                }
                case NPAT_YOY -> {
                    if (v > 10) highlights.add(String.format("Net profit grew %.1f%% year-over-year", v));
                    else if (v < 0) highlights.add(String.format("Net profit declined %.1f%% year-over-year", Math.abs(v)));
                }
                case NET_MARGIN -> {
                    if (v > 10) highlights.add(String.format("Net margin is healthy at %.1f%%", v));
                }
                case POSITIVE_NPAT_LAST_4Q -> {
                    if (v == 1.0) highlights.add("Positive profit in last 4 consecutive quarters");
                }
                default -> {}
            }
        }
        return highlights;
    }

    private String resolveDataQuality(String ticker, String period, Long batchId) {
        List<FaFinancialMetricEntity> metrics = metricRepo.findByTickerAndPeriodCodeAndImportBatchId(
                ticker, period, batchId);
        if (metrics.isEmpty()) return "NO_DATA";
        long ok = metrics.stream().filter(m -> m.getQualityStatus() == QualityStatus.OK).count();
        double pct = 100.0 * ok / metrics.size();
        if (pct >= 90) return "OK";
        if (pct >= 70) return "PARTIAL";
        return "POOR";
    }

    public TickerScoreHistoryResponse getScoreHistory(String ticker) {
        List<FaScoreSnapshotEntity> scores = scoreRepo.findByTickerAndImportBatchId(
                ticker.toUpperCase(), resolveLatestBatch(null));

        // Also include all batches for richer history
        List<FaScoreSnapshotEntity> allScores = scoreRepo.findAll().stream()
                .filter(s -> s.getTicker().equalsIgnoreCase(ticker))
                .sorted(Comparator.comparing(FaScoreSnapshotEntity::getPeriodCode))
                .toList();

        List<TickerScoreHistoryResponse.ScorePoint> history = allScores.stream()
                .map(s -> TickerScoreHistoryResponse.ScorePoint.builder()
                        .period(s.getPeriodCode())
                        .overallScore(s.getOverallScore())
                        .growthScore(s.getGrowthScore())
                        .profitabilityScore(s.getProfitabilityScore())
                        .valuationScore(s.getValuationScore())
                        .stabilityScore(s.getStabilityScore())
                        .dataQualityScore(s.getDataQualityScore())
                        .rating(s.getRating())
                        .explanation(s.getExplanation())
                        .batchId(s.getImportBatchId())
                        .calculatedAt(s.getCreatedAt())
                        .build())
                .toList();

        return TickerScoreHistoryResponse.builder()
                .ticker(ticker.toUpperCase())
                .history(history)
                .build();
    }
}
