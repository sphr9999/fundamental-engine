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
 * Phase 1 ratios (v1.0):
 * - REVENUE_YOY, NPAT_YOY (year-over-year growth %)
 * - REVENUE_QOQ, NPAT_QOQ (quarter-over-quarter growth %)
 * - GROSS_MARGIN, NET_MARGIN (margin ratios)
 * - MARKET_CAP (price × shares)
 * - PE_TTM (price / EPS TTM)
 * - POSITIVE_NPAT_LAST_4Q (bool: all 4 recent quarters positive)
 *
 * Phase 2A ratios (v2.0):
 * - ROE, ROA, OPERATING_MARGIN (profitability)
 * - DEBT_TO_EQUITY, CURRENT_RATIO, QUICK_RATIO, INTEREST_COVERAGE (solvency)
 * - ASSET_TURNOVER (efficiency)
 * - FREE_CASH_FLOW, CFO_TO_NET_INCOME, CAPEX_TO_REVENUE (cash flow)
 * - BOOK_VALUE_PER_SHARE (per share)
 * - NET_DEBT (balance sheet derived)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatioCalculationService {

    private static final String CALC_VERSION = "v2.0";
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_QUARTER = BigDecimal.valueOf(90);

    private final FaFinancialMetricRepository metricRepo;
    private final FaFinancialRatioRepository ratioRepo;

    /**
     * Calculate all ratios for every ticker in the batch.
     * Should be called after Excel import or VCI API enrichment completes.
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

        // ═══════════════════════════════════════════════════════════════
        // Phase 1 ratios
        // ═══════════════════════════════════════════════════════════════

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

        // ═══════════════════════════════════════════════════════════════
        // Phase 2A ratios (only calculated if enriched data is available)
        // ═══════════════════════════════════════════════════════════════

        // ── ROE = NPAT / Total Equity × 100 ──────────────────────────
        ratio(ticker, reportPeriod, RatioCode.ROE, batchId,
                calcRatio(idx, MetricCode.NPAT, MetricCode.TOTAL_EQUITY, reportPeriod, true))
                .ifPresent(result::add);

        // ── ROA = NPAT / Total Assets × 100 ──────────────────────────
        ratio(ticker, reportPeriod, RatioCode.ROA, batchId,
                calcRatio(idx, MetricCode.NPAT, MetricCode.TOTAL_ASSETS, reportPeriod, true))
                .ifPresent(result::add);

        // ── Operating Margin = Operating Profit / Revenue × 100 ──────
        ratio(ticker, reportPeriod, RatioCode.OPERATING_MARGIN, batchId,
                calcMargin(idx, MetricCode.OPERATING_PROFIT, MetricCode.REVENUE, reportPeriod))
                .ifPresent(result::add);

        // ── EBITDA Margin ────────────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.EBITDA_MARGIN, batchId,
                calcEbitdaMargin(idx, reportPeriod))
                .ifPresent(result::add);

        // ── Debt to Equity ───────────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.DEBT_TO_EQUITY, batchId,
                calcDebtToEquity(idx, reportPeriod))
                .ifPresent(result::add);

        // ── Net Debt = (ST Debt + LT Debt) - Cash ────────────────────
        ratio(ticker, reportPeriod, RatioCode.NET_DEBT, batchId,
                calcNetDebt(idx, reportPeriod))
                .ifPresent(result::add);

        // ── Current Ratio = Current Assets / Current Liabilities ─────
        ratio(ticker, reportPeriod, RatioCode.CURRENT_RATIO, batchId,
                calcRatio(idx, MetricCode.CURRENT_ASSETS, MetricCode.CURRENT_LIABILITIES, reportPeriod, false))
                .ifPresent(result::add);

        // ── Quick Ratio = (Current Assets - Inventory) / Current Liabilities
        ratio(ticker, reportPeriod, RatioCode.QUICK_RATIO, batchId,
                calcQuickRatio(idx, reportPeriod))
                .ifPresent(result::add);

        // ── Interest Coverage = Operating Profit / Interest Expenses ─
        ratio(ticker, reportPeriod, RatioCode.INTEREST_COVERAGE, batchId,
                calcRatio(idx, MetricCode.OPERATING_PROFIT, MetricCode.INTEREST_EXPENSES, reportPeriod, false))
                .ifPresent(result::add);

        // ── Asset Turnover = Revenue / Total Assets ──────────────────
        ratio(ticker, reportPeriod, RatioCode.ASSET_TURNOVER, batchId,
                calcRatio(idx, MetricCode.REVENUE, MetricCode.TOTAL_ASSETS, reportPeriod, false))
                .ifPresent(result::add);

        // ── Free Cash Flow = CFO - |CAPEX| ───────────────────────────
        ratio(ticker, reportPeriod, RatioCode.FREE_CASH_FLOW, batchId,
                calcFreeCashFlow(idx, reportPeriod))
                .ifPresent(result::add);

        // ── CFO / Net Income ─────────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.CFO_TO_NET_INCOME, batchId,
                calcRatio(idx, MetricCode.CFO, MetricCode.NPAT, reportPeriod, false))
                .ifPresent(result::add);

        // ── CAPEX / Revenue × 100 ────────────────────────────────────
        ratio(ticker, reportPeriod, RatioCode.CAPEX_TO_REVENUE, batchId,
                calcCapexToRevenue(idx, reportPeriod))
                .ifPresent(result::add);

        // ── Book Value Per Share = Total Equity / Shares Outstanding ─
        ratio(ticker, reportPeriod, RatioCode.BOOK_VALUE_PER_SHARE, batchId,
                calcRatio(idx, MetricCode.TOTAL_EQUITY, MetricCode.SHARES_OUTSTANDING, reportPeriod, false))
                .ifPresent(result::add);

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Phase 1 calculation helpers (unchanged)
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
                .multiply(HUNDRED);
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
                .multiply(HUNDRED);
        return RatioResult.ok(qoq);
    }

    private RatioResult calcMargin(Map<String, FaFinancialMetricEntity> idx,
                                    MetricCode numerator, MetricCode denominator, String period) {
        var num = getOkValue(idx, numerator, period);
        var den = getOkValue(idx, denominator, period);
        if (num == null) return RatioResult.missing(numerator + " missing");
        if (den == null || den.compareTo(BigDecimal.ZERO) == 0) return RatioResult.missing("Revenue is zero or missing");
        BigDecimal margin = num.divide(den, SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
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
    // Phase 2A calculation helpers (new)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generic ratio: numerator / denominator.
     * If asPercentage is true, multiplies by 100.
     */
    private RatioResult calcRatio(Map<String, FaFinancialMetricEntity> idx,
                                   MetricCode numerator, MetricCode denominator,
                                   String period, boolean asPercentage) {
        var num = getOkValue(idx, numerator, period);
        var den = getOkValue(idx, denominator, period);
        if (num == null) return RatioResult.missing(numerator + " missing");
        if (den == null || den.compareTo(BigDecimal.ZERO) == 0) {
            return RatioResult.missing(denominator + " is zero or missing");
        }
        BigDecimal result = num.divide(den, SCALE, RoundingMode.HALF_UP);
        if (asPercentage) {
            result = result.multiply(HUNDRED);
        }
        return RatioResult.ok(result);
    }

    /**
     * EBITDA Margin = (Operating Profit + Depreciation) / Revenue × 100
     */
    private RatioResult calcEbitdaMargin(Map<String, FaFinancialMetricEntity> idx, String period) {
        var opProfit = getOkValue(idx, MetricCode.OPERATING_PROFIT, period);
        var depreciation = getOkValue(idx, MetricCode.DEPRECIATION_AMORTIZATION, period);
        var revenue = getOkValue(idx, MetricCode.REVENUE, period);
        if (opProfit == null) return RatioResult.missing("Operating profit missing");
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return RatioResult.missing("Revenue is zero or missing");
        }
        // Depreciation may be missing — treat as 0 if not available
        BigDecimal dep = depreciation != null ? depreciation : BigDecimal.ZERO;
        BigDecimal ebitda = opProfit.add(dep);
        BigDecimal margin = ebitda.divide(revenue, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
        return RatioResult.ok(margin);
    }

    /**
     * Debt to Equity = (Short Term Debt + Long Term Debt) / Total Equity
     */
    private RatioResult calcDebtToEquity(Map<String, FaFinancialMetricEntity> idx, String period) {
        var stDebt = getOkValue(idx, MetricCode.SHORT_TERM_DEBT, period);
        var ltDebt = getOkValue(idx, MetricCode.LONG_TERM_DEBT, period);
        var equity = getOkValue(idx, MetricCode.TOTAL_EQUITY, period);
        if (equity == null || equity.compareTo(BigDecimal.ZERO) == 0) {
            return RatioResult.missing("Total equity is zero or missing");
        }
        BigDecimal totalDebt = (stDebt != null ? stDebt : BigDecimal.ZERO)
                .add(ltDebt != null ? ltDebt : BigDecimal.ZERO);
        if (totalDebt.compareTo(BigDecimal.ZERO) == 0 && stDebt == null && ltDebt == null) {
            return RatioResult.missing("Debt data missing");
        }
        return RatioResult.ok(totalDebt.divide(equity, SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Net Debt = (Short Term Debt + Long Term Debt) - Cash
     */
    private RatioResult calcNetDebt(Map<String, FaFinancialMetricEntity> idx, String period) {
        var stDebt = getOkValue(idx, MetricCode.SHORT_TERM_DEBT, period);
        var ltDebt = getOkValue(idx, MetricCode.LONG_TERM_DEBT, period);
        var cash = getOkValue(idx, MetricCode.CASH_AND_EQUIVALENTS, period);
        if (stDebt == null && ltDebt == null) return RatioResult.missing("Debt data missing");
        if (cash == null) return RatioResult.missing("Cash data missing");
        BigDecimal totalDebt = (stDebt != null ? stDebt : BigDecimal.ZERO)
                .add(ltDebt != null ? ltDebt : BigDecimal.ZERO);
        return RatioResult.ok(totalDebt.subtract(cash));
    }

    /**
     * Quick Ratio = (Current Assets - Inventory) / Current Liabilities
     */
    private RatioResult calcQuickRatio(Map<String, FaFinancialMetricEntity> idx, String period) {
        var currentAssets = getOkValue(idx, MetricCode.CURRENT_ASSETS, period);
        var inventory = getOkValue(idx, MetricCode.INVENTORY, period);
        var currentLiabilities = getOkValue(idx, MetricCode.CURRENT_LIABILITIES, period);
        if (currentAssets == null) return RatioResult.missing("Current assets missing");
        if (currentLiabilities == null || currentLiabilities.compareTo(BigDecimal.ZERO) == 0) {
            return RatioResult.missing("Current liabilities is zero or missing");
        }
        BigDecimal inv = inventory != null ? inventory : BigDecimal.ZERO;
        BigDecimal quickAssets = currentAssets.subtract(inv);
        return RatioResult.ok(quickAssets.divide(currentLiabilities, SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Free Cash Flow = CFO - |CAPEX|
     * CAPEX is typically negative (cash outflow), so we take its absolute value.
     */
    private RatioResult calcFreeCashFlow(Map<String, FaFinancialMetricEntity> idx, String period) {
        var cfo = getOkValue(idx, MetricCode.CFO, period);
        var capex = getOkValue(idx, MetricCode.CAPEX, period);
        if (cfo == null) return RatioResult.missing("CFO missing");
        if (capex == null) return RatioResult.missing("CAPEX missing");
        // CAPEX is typically negative, so FCF = CFO - |CAPEX| = CFO + CAPEX (if CAPEX is negative)
        // But to be safe, always use absolute: FCF = CFO - abs(CAPEX)
        return RatioResult.ok(cfo.subtract(capex.abs()));
    }

    /**
     * CAPEX to Revenue = |CAPEX| / Revenue × 100
     */
    private RatioResult calcCapexToRevenue(Map<String, FaFinancialMetricEntity> idx, String period) {
        var capex = getOkValue(idx, MetricCode.CAPEX, period);
        var revenue = getOkValue(idx, MetricCode.REVENUE, period);
        if (capex == null) return RatioResult.missing("CAPEX missing");
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return RatioResult.missing("Revenue is zero or missing");
        }
        return RatioResult.ok(capex.abs().divide(revenue, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED));
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
