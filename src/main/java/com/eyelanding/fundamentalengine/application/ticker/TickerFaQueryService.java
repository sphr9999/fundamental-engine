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
import com.eyelanding.fundamentalengine.infrastructure.vci.VciHttpClient;
import com.eyelanding.fundamentalengine.infrastructure.vci.dto.VciCompanyInfo;
import com.eyelanding.fundamentalengine.infrastructure.vps.VpsHttpClient;
import com.eyelanding.fundamentalengine.infrastructure.vps.VpsStockData;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TickerFaQueryService {

    private static final Logger log = LoggerFactory.getLogger(TickerFaQueryService.class);

    /** Market-data metrics that may only exist in Excel import batches, not VCI enrichment batches */
    private static final Set<MetricCode> MARKET_DATA_METRICS = Set.of(
            MetricCode.CLOSE_PRICE, MetricCode.PB, MetricCode.SHARES_OUTSTANDING, MetricCode.EPS_DILUTED
    );

    private final FaCompanyRepository companyRepo;
    private final FaFinancialMetricRepository metricRepo;
    private final FaFinancialRatioRepository ratioRepo;
    private final FaScoreSnapshotRepository scoreRepo;
    private final FaImportBatchRepository batchRepo;
    private final VciHttpClient vciHttpClient;
    private final VpsHttpClient vpsHttpClient;

    @Cacheable(value = RedisCacheConfig.CACHE_TICKER_OVERVIEW, key = "#ticker.toUpperCase() + ':' + #period + ':' + #batchId")
    public TickerOverviewResponse getOverview(String ticker, String period, Long batchId) {
        String effectivePeriod = period != null ? period : "2026Q1";
        Long effectiveBatch = resolveLatestBatch(batchId);
        String upperTicker = ticker.toUpperCase();

        // Company info
        Optional<FaCompanyEntity> company = companyRepo.findByTicker(upperTicker);

        // DB metrics (across batches for fallback)
        Map<MetricCode, BigDecimal> metrics = getMetricValuesAcrossBatches(upperTicker, effectivePeriod);

        // Latest score
        Optional<FaScoreSnapshotEntity> score = scoreRepo
                .findTopByTickerAndPeriodCodeAndImportBatchIdOrderByCreatedAtDesc(
                        upperTicker, effectivePeriod, effectiveBatch);

        // Highlights & warnings
        List<String> highlights = buildHighlights(ticker, effectivePeriod, effectiveBatch);
        List<String> warnings = new ArrayList<>();

        // ── Fetch external market data ──────────────────────────────
        // VCI: always fetch for analyst data (targetPrice, rating, 52-week range)
        Optional<VciCompanyInfo> vciInfo = fetchVciSafe(upperTicker);

        // VPS: realtime intraday price (last matched price)
        Optional<VpsStockData> vpsData = fetchVpsSafe(upperTicker);

        // ── 3-tier price resolution: VPS → VCI → Excel ──────────────
        BigDecimal price;
        BigDecimal marketCap;
        BigDecimal peTtm;
        BigDecimal pb;
        String priceSource;

        // VCI analyst data (available regardless of price source)
        BigDecimal targetPrice = vciInfo.map(VciCompanyInfo::getTargetPrice).orElse(null);
        String analystRating = vciInfo.map(VciCompanyInfo::getRating).orElse(null);
        BigDecimal highestPrice1Year = vciInfo.map(VciCompanyInfo::getHighestPrice1Year).orElse(null);
        BigDecimal lowestPrice1Year = vciInfo.map(VciCompanyInfo::getLowestPrice1Year).orElse(null);

        // Shares for market cap calculation (VCI > DB fallback)
        BigDecimal shares = vciInfo.map(VciCompanyInfo::getNumberOfSharesMktCap).orElse(null);
        if (shares == null) {
            shares = metrics.get(MetricCode.SHARES_OUTSTANDING);
        }

        BigDecimal eps = metrics.get(MetricCode.EPS_DILUTED);
        BigDecimal totalEquity = metrics.get(MetricCode.TOTAL_EQUITY);

        if (vpsData.isPresent() && vpsData.get().getLastPrice() != null
                && vpsData.get().getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
            // ── Tier 1: VPS realtime intraday ──
            price = vpsData.get().getLastPrice();
            marketCap = (shares != null) ? price.multiply(shares) : null;
            priceSource = "VPS_REALTIME";

            log.debug("Ticker {} using VPS realtime price: {}", upperTicker, price);

        } else if (vciInfo.isPresent() && vciInfo.get().getCurrentPrice() != null) {
            // ── Tier 2: VCI latest close (T-1) ──
            price = vciInfo.get().getCurrentPrice();
            marketCap = vciInfo.get().getMarketCap();
            priceSource = "VCI_LATEST_CLOSE";

            log.debug("Ticker {} using VCI latest close: {}", upperTicker, price);

        } else {
            // ── Tier 3: Excel import fallback ──
            price = metrics.get(MetricCode.CLOSE_PRICE);
            BigDecimal excelShares = metrics.get(MetricCode.SHARES_OUTSTANDING);
            marketCap = (price != null && excelShares != null) ? price.multiply(excelShares) : null;
            priceSource = "EXCEL_IMPORT";

            warnings.add("Price data from Excel import (may be outdated)");
            log.debug("Ticker {} using Excel fallback price: {}", upperTicker, price);
        }

        // PE = price / EPS
        peTtm = (price != null && eps != null && eps.compareTo(BigDecimal.ZERO) != 0)
                ? price.divide(eps, 2, RoundingMode.HALF_UP) : null;

        // PB = marketCap / totalEquity (option A — realtime)
        if (marketCap != null && totalEquity != null && totalEquity.compareTo(BigDecimal.ZERO) > 0) {
            pb = marketCap.divide(totalEquity, 2, RoundingMode.HALF_UP);
        } else {
            pb = metrics.get(MetricCode.PB); // fallback to Excel PB
        }

        warnings.add("Cash flow data is not available in Phase 1");

        return TickerOverviewResponse.builder()
                .ticker(upperTicker)
                .companyName(company.map(FaCompanyEntity::getCompanyName).orElse(null))
                .exchange(company.map(FaCompanyEntity::getExchange).orElse(null))
                .industry(company.map(FaCompanyEntity::getIndustryLevel1).orElse(null))
                .period(effectivePeriod)
                .price(price)
                .pb(pb)
                .peTtm(peTtm)
                .marketCap(marketCap)
                .targetPrice(targetPrice)
                .analystRating(analystRating)
                .highestPrice1Year(highestPrice1Year)
                .lowestPrice1Year(lowestPrice1Year)
                .priceSource(priceSource)
                .faScore(score.map(FaScoreSnapshotEntity::getOverallScore).orElse(null))
                .rating(score.map(FaScoreSnapshotEntity::getRating).orElse(null))
                .dataQuality(resolveDataQuality(upperTicker, effectivePeriod, effectiveBatch))
                .growthScore(score.map(FaScoreSnapshotEntity::getGrowthScore).orElse(null))
                .profitabilityScore(score.map(FaScoreSnapshotEntity::getProfitabilityScore).orElse(null))
                .valuationScore(score.map(FaScoreSnapshotEntity::getValuationScore).orElse(null))
                .stabilityScore(score.map(FaScoreSnapshotEntity::getStabilityScore).orElse(null))
                .dataQualityScore(score.map(FaScoreSnapshotEntity::getDataQualityScore).orElse(null))
                .highlights(highlights)
                .warnings(warnings)
                .build();
    }

    // ── Safe external API fetch helpers ──────────────────────────────

    private Optional<VciCompanyInfo> fetchVciSafe(String ticker) {
        try {
            return vciHttpClient.fetchCompanyInfo(ticker);
        } catch (Exception e) {
            log.warn("Failed to fetch VCI company info for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<VpsStockData> fetchVpsSafe(String ticker) {
        try {
            return vpsHttpClient.fetchRealtimePrice(ticker);
        } catch (Exception e) {
            log.warn("Failed to fetch VPS realtime price for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
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

    /**
     * Search for metrics across ALL batches (newest first).
     * For market-data metrics (CLOSE_PRICE, PB, SHARES_OUTSTANDING, EPS_DILUTED),
     * if the latest batch doesn't have them, fall back to older batches.
     * This handles the case where VCI enrichment batches (SUCCESS) don't contain
     * price/PB data — only the original Excel import batches do.
     */
    private Map<MetricCode, BigDecimal> getMetricValuesAcrossBatches(String ticker, String period) {
        // Get all batches ordered newest first (both SUCCESS and PARTIAL_SUCCESS)
        List<Long> batchIds = batchRepo.findAll().stream()
                .filter(b -> b.getStatus() == com.eyelanding.fundamentalengine.domain.ImportStatus.SUCCESS
                        || b.getStatus() == com.eyelanding.fundamentalengine.domain.ImportStatus.PARTIAL_SUCCESS)
                .sorted(Comparator.comparing(b -> b.getCreatedAt(), Comparator.reverseOrder()))
                .map(b -> b.getId())
                .toList();

        Map<MetricCode, BigDecimal> result = new HashMap<>();

        for (Long bId : batchIds) {
            Map<MetricCode, BigDecimal> batchMetrics = getMetricValues(ticker, period, bId);
            for (var entry : batchMetrics.entrySet()) {
                // Only fill in metrics that haven't been found yet (newest batch wins)
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }

            // Check if we have all the critical market-data metrics
            if (MARKET_DATA_METRICS.stream().allMatch(result::containsKey)) {
                break; // Got everything we need
            }
        }

        if (log.isDebugEnabled()) {
            Set<MetricCode> missing = MARKET_DATA_METRICS.stream()
                    .filter(mc -> !result.containsKey(mc))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                log.debug("Ticker {} period {}: still missing market-data metrics after scanning all batches: {}",
                        ticker, period, missing);
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
