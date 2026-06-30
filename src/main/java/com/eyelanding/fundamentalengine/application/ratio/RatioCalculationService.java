package com.eyelanding.fundamentalengine.application.ratio;

import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.PeriodType;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.domain.RatioCode;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialMetricEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialRatioEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaFinancialMetricRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaFinancialRatioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates financial ratios for all tickers in a given import batch.
 *
 * Ratios calculated:
 * - REVENUE_YOY, NPAT_YOY (year-over-year growth %)
 * - REVENUE_QOQ, NPAT_QOQ (quarter-over-quarter growth %)
 * - GROSS_MARGIN, NET_MARGIN (margin ratios)
 * - MARKET_CAP (price × shares)
 * - PE_TTM (price / EPS TTM)
 * - POSITIVE_NPAT_LAST_4Q (bool: all 4 recent quarters positive)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatioCalculationService {

    private static final String CALC_VERSION = "v1.0";
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 8;

    private final FaFinancialMetricRepository metricRepo;
    private final FaFinancialRatioRepository ratioRepo;

    /**
     * Calculate all ratios for every ticker in the batch.
     * Should be called after Excel import completes.
     */
    @Transactional
    public void calculateForBatch(Long batchId, String reportPeriod) {
        log.info("Starting ratio calculation for batch={}, period={}", batchId, reportPeriod);

        // Get all metrics for this batch, grouped by ticker
        List<FaFinancialMetricEntity> allMetrics = metricRepo.findByImportBatchId(batchId);
        Map<String, List<FaFinancialMetricEntity>> byTicker = allMetrics.stream()
                .collect(Collectors.groupingBy(FaFinancialMetricEntity::getTicker));

        List<FaFinancialRatioEntity> ratiosToSave = new ArrayList<>();

        for (Map.Entry<String, List<FaFinancialMetricEntity>> entry : byTicker.entrySet()) {
            String ticker = entry.getKey();
            List<FaFinancialMetricEntity> metrics = entry.getValue();
            List<FaFinancialRatioEntity> tickerRatios = calculateForTicker(ticker, metrics, batchId, reportPeriod);
            ratiosToSave.addAll(tickerRatios);
        }

        if (!ratiosToSave.isEmpty()) {
            ratioRepo.saveAll(ratiosToSave);
        }

        log.info("Ratio calculation complete: {} ratios saved for batch={}", ratiosToSave.size(), batchId);
    }

    private List<FaFinancialRatioEntity> calculateForTicker(String ticker,
                                                              List<FaFinancialMetricEntity> metrics,
                                                              Long batchId, String reportPeriod) {
        List<FaFinancialRatioEntity> result = new ArrayList<>();

        // Index metrics by (metricCode, periodCode) for easy lookup
        Map<String, FaFinancialMetricEntity> idx = new HashMap<>();
        for (var m : metrics) {
            idx.put(m.getMetricCode().name() + "|" + m.getPeriodCode(), m);
        }

        // ── Revenue YoY ──────────────────────────────────────────────
        String prevYearPeriod = getPreviousYearPeriod(reportPeriod);
        ratio(ticker, reportPeriod, RatioCode.REVENUE_YOY, batchId,
                calcYoY(idx, MetricCode.REVENUE, reportPeriod, prevYearPeriod))
                .ifPresent(result::add);

        // ── NPAT YoY ─────────────────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.NPAT_YOY, batchId,
                calcYoY(idx, MetricCode.NPAT, reportPeriod, prevYearPeriod))
                .ifPresent(result::add);

        // ── Revenue QoQ ──────────────────────────────────────────────
        String prevQtrPeriod = getPreviousQuarter(reportPeriod);
        ratio(ticker, reportPeriod, RatioCode.REVENUE_QOQ, batchId,
                calcQoQ(idx, MetricCode.REVENUE, reportPeriod, prevQtrPeriod))
                .ifPresent(result::add);

        // ── NPAT QoQ ─────────────────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.NPAT_QOQ, batchId,
                calcQoQ(idx, MetricCode.NPAT, reportPeriod, prevQtrPeriod))
                .ifPresent(result::add);

        // ── Gross Margin = Gross Profit / Revenue ────────────────────
        ratio(ticker, reportPeriod, RatioCode.GROSS_MARGIN, batchId,
                calcMargin(idx, MetricCode.GROSS_PROFIT, MetricCode.REVENUE, reportPeriod))
                .ifPresent(result::add);

        // ── Net Margin = NPAT / Revenue ──────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.NET_MARGIN, batchId,
                calcMargin(idx, MetricCode.NPAT, MetricCode.REVENUE, reportPeriod))
                .ifPresent(result::add);

        // ── Market Cap = Price × Shares ───────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.MARKET_CAP, batchId,
                calcMarketCap(idx, reportPeriod))
                .ifPresent(result::add);

        // ── PE TTM = Price / EPS TTM ─────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.PE_TTM, batchId,
                calcPE(idx, reportPeriod))
                .ifPresent(result::add);

        // ── PB (stored directly from import) ─────────────────────────
        var pbMetric = idx.get(MetricCode.PB.name() + "|" + reportPeriod);
        if (pbMetric != null && pbMetric.getQualityStatus() == QualityStatus.OK && pbMetric.getMetricValue() != null) {
            ratio(ticker, reportPeriod, RatioCode.PB, batchId,
                    RatioResult.ok(pbMetric.getMetricValue()))
                    .ifPresent(result::add);
        }

        // ── Positive NPAT last 4 quarters ────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.POSITIVE_NPAT_LAST_4Q, batchId,
                calcPositiveNpatLast4Q(idx, reportPeriod))
                .ifPresent(result::add);

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Calculation helpers
    // ═══════════════════════════════════════════════════════════════

    private RatioResult calcYoY(Map<String, FaFinancialMetricEntity> idx,
                                 MetricCode metric, String currentPeriod, String prevPeriod) {
        if (prevPeriod == null) return RatioResult.missing("Cannot determine previous year period");
        var current = getOkValue(idx, metric, currentPeriod);
        var previous = getOkValue(idx, metric, prevPeriod);
        if (current == null) return RatioResult.missing("Current period value missing");
        if (previous == null) return RatioResult.missing("Previous year value missing");
        if (previous.compareTo(BigDecimal.ZERO) == 0) return RatioResult.missing("Division by zero: previous = 0");
        BigDecimal yoy = current.subtract(previous).divide(previous.abs(), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return RatioResult.ok(yoy);
    }

    private RatioResult calcQoQ(Map<String, FaFinancialMetricEntity> idx,
                                 MetricCode metric, String currentPeriod, String prevPeriod) {
        if (prevPeriod == null) return RatioResult.missing("Cannot determine previous quarter");
        var current = getOkValue(idx, metric, currentPeriod);
        var previous = getOkValue(idx, metric, prevPeriod);
        if (current == null) return RatioResult.missing("Current quarter value missing");
        if (previous == null) return RatioResult.missing("Previous quarter value missing");
        if (previous.compareTo(BigDecimal.ZERO) == 0) return RatioResult.missing("Division by zero: previous = 0");
        BigDecimal qoq = current.subtract(previous).divide(previous.abs(), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return RatioResult.ok(qoq);
    }

    private RatioResult calcMargin(Map<String, FaFinancialMetricEntity> idx,
                                    MetricCode numerator, MetricCode denominator, String period) {
        var num = getOkValue(idx, numerator, period);
        var den = getOkValue(idx, denominator, period);
        if (num == null) return RatioResult.missing(numerator + " missing");
        if (den == null || den.compareTo(BigDecimal.ZERO) == 0) return RatioResult.missing("Revenue is zero or missing");
        BigDecimal margin = num.divide(den, SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return RatioResult.ok(margin);
    }

    private RatioResult calcMarketCap(Map<String, FaFinancialMetricEntity> idx, String period) {
        var price = getOkValue(idx, MetricCode.CLOSE_PRICE, period);
        var shares = getOkValue(idx, MetricCode.SHARES_OUTSTANDING, period);
        if (price == null) return RatioResult.missing("Price missing");
        if (shares == null) return RatioResult.missing("Shares outstanding missing");
        return RatioResult.ok(price.multiply(shares, MC));
    }

    private RatioResult calcPE(Map<String, FaFinancialMetricEntity> idx, String period) {
        var price = getOkValue(idx, MetricCode.CLOSE_PRICE, period);
        var eps = getOkValue(idx, MetricCode.EPS_DILUTED, period);
        if (price == null) return RatioResult.missing("Price missing");
        if (eps == null || eps.compareTo(BigDecimal.ZERO) == 0) return RatioResult.missing("EPS is zero or missing");
        return RatioResult.ok(price.divide(eps, SCALE, RoundingMode.HALF_UP));
    }

    private RatioResult calcPositiveNpatLast4Q(Map<String, FaFinancialMetricEntity> idx, String latestPeriod) {
        List<String> last4 = getLast4Quarters(latestPeriod);
        if (last4.isEmpty()) return RatioResult.missing("Cannot determine last 4 quarters");

        int positiveCount = 0;
        for (String period : last4) {
            var val = getOkValue(idx, MetricCode.NPAT, period);
            if (val != null && val.compareTo(BigDecimal.ZERO) > 0) positiveCount++;
        }
        // Return 1 if all 4 positive, 0 otherwise
        return RatioResult.ok(positiveCount == 4 ? BigDecimal.ONE : BigDecimal.ZERO);
    }

    // ═══════════════════════════════════════════════════════════════
    // Period helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Given "2026Q1" → returns "2025Q1" (same quarter, previous year)
     */
    static String getPreviousYearPeriod(String period) {
        if (period == null) return null;
        try {
            // Format: 2026Q1
            int year = Integer.parseInt(period.substring(0, 4));
            String qPart = period.substring(4); // "Q1"
            return (year - 1) + qPart;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Given "2026Q1" → returns "2025Q4" (previous quarter)
     */
    static String getPreviousQuarter(String period) {
        if (period == null) return null;
        try {
            int year = Integer.parseInt(period.substring(0, 4));
            int quarter = Integer.parseInt(period.substring(5));
            if (quarter == 1) {
                return (year - 1) + "Q4";
            } else {
                return year + "Q" + (quarter - 1);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Given "2026Q1" → returns ["2026Q1", "2025Q4", "2025Q3", "2025Q2"]
     */
    static List<String> getLast4Quarters(String latestPeriod) {
        List<String> result = new ArrayList<>();
        String current = latestPeriod;
        for (int i = 0; i < 4; i++) {
            if (current == null) break;
            result.add(current);
            current = getPreviousQuarter(current);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════

    private BigDecimal getOkValue(Map<String, FaFinancialMetricEntity> idx,
                                   MetricCode metric, String period) {
        var entity = idx.get(metric.name() + "|" + period);
        if (entity == null) return null;
        if (entity.getQualityStatus() != QualityStatus.OK) return null;
        return entity.getMetricValue();
    }

    private Optional<FaFinancialRatioEntity> ratio(String ticker, String period,
                                                    RatioCode ratioCode, Long batchId,
                                                    RatioResult result) {
        return Optional.of(FaFinancialRatioEntity.builder()
                .ticker(ticker)
                .periodCode(period)
                .ratioCode(ratioCode)
                .ratioValue(result.value())
                .qualityStatus(result.quality())
                .qualityNote(result.note())
                .calculationVersion(CALC_VERSION)
                .importBatchId(batchId)
                .build());
    }

    /** Internal result carrier for ratio calculations */
    private record RatioResult(BigDecimal value, QualityStatus quality, String note) {
        static RatioResult ok(BigDecimal v) {
            return new RatioResult(v, QualityStatus.OK, null);
        }
        static RatioResult missing(String note) {
            return new RatioResult(null, QualityStatus.MISSING, note);
        }
    }
}
