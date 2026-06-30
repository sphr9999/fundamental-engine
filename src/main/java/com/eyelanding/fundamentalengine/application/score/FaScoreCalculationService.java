package com.eyelanding.fundamentalengine.application.score;

import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.domain.RatioCode;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialRatioEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaScoreSnapshotEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaFinancialRatioRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaScoreSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based FA Score Engine (0-100).
 *
 * Phase 2A Score breakdown (100 pts total):
 * ┌─────────────────────┬────────┐
 * │ Growth              │ 20 pts │  Revenue YoY + NPAT YoY
 * │ Profitability       │ 20 pts │  Net Margin + Gross Margin + ROE + Operating Margin
 * │ Valuation           │ 20 pts │  PE, PB (lower = higher score)
 * │ Solvency            │ 15 pts │  D/E + Current Ratio + Interest Coverage
 * │ Cash Flow           │ 10 pts │  CFO > 0, FCF > 0, CFO/NI quality
 * │ Stability           │  5 pts │  Positive NPAT last 4Q
 * │ Data Quality        │ 10 pts │  % of metrics with OK status
 * └─────────────────────┴────────┘
 *
 * Rating:
 * ≥80 → STRONG_FA
 * ≥65 → GOOD_FA
 * ≥50 → FAIR_FA
 * ≥35 → WEAK_FA
 * <35  → POOR_FA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaScoreCalculationService {

    private static final String CALC_VERSION = "v2.0";

    private final FaFinancialRatioRepository ratioRepo;
    private final FaScoreSnapshotRepository scoreRepo;

    @Transactional
    public void calculateForBatch(Long batchId, String reportPeriod) {
        log.info("Starting FA score calculation for batch={}, period={}", batchId, reportPeriod);

        List<FaFinancialRatioEntity> allRatios = ratioRepo.findAll().stream()
                .filter(r -> r.getImportBatchId().equals(batchId) && r.getPeriodCode().equals(reportPeriod))
                .toList();

        Map<String, List<FaFinancialRatioEntity>> byTicker = allRatios.stream()
                .collect(Collectors.groupingBy(FaFinancialRatioEntity::getTicker));

        List<FaScoreSnapshotEntity> snapshots = new ArrayList<>();
        for (var entry : byTicker.entrySet()) {
            snapshots.add(scoreOneTicker(entry.getKey(), entry.getValue(), batchId, reportPeriod));
        }

        if (!snapshots.isEmpty()) {
            scoreRepo.saveAll(snapshots);
        }
        log.info("FA score calculation complete: {} tickers scored for batch={}", snapshots.size(), batchId);
    }

    private FaScoreSnapshotEntity scoreOneTicker(String ticker,
                                                   List<FaFinancialRatioEntity> ratios,
                                                   Long batchId, String period) {
        // Index ratios by code
        Map<RatioCode, BigDecimal> idx = new HashMap<>();
        for (var r : ratios) {
            if (r.getQualityStatus() == QualityStatus.OK && r.getRatioValue() != null) {
                idx.put(r.getRatioCode(), r.getRatioValue());
            }
        }

        // Detect if enriched data is available (check for Phase 2A ratios)
        boolean hasEnrichedData = idx.containsKey(RatioCode.ROE) ||
                idx.containsKey(RatioCode.DEBT_TO_EQUITY) ||
                idx.containsKey(RatioCode.FREE_CASH_FLOW);

        // ── Growth Score (20 pts) ──────────────────────────────────
        double growthScore = 0;
        // Revenue YoY (10 pts): >20%=10, >10%=7, >0%=3, ≤0%=0
        growthScore += scoreGrowth(idx.get(RatioCode.REVENUE_YOY), 10);
        // NPAT YoY (10 pts)
        growthScore += scoreGrowth(idx.get(RatioCode.NPAT_YOY), 10);

        // ── Profitability Score (20 pts) ──────────────────────────
        double profitScore = 0;
        if (hasEnrichedData) {
            // With enriched data: Net Margin (5) + Gross Margin (5) + ROE (5) + Operating Margin (5)
            profitScore += scoreMargin(idx.get(RatioCode.NET_MARGIN), new double[]{15, 8, 0}, 5);
            profitScore += scoreMargin(idx.get(RatioCode.GROSS_MARGIN), new double[]{30, 15, 0}, 5);
            profitScore += scoreROE(idx.get(RatioCode.ROE), 5);
            profitScore += scoreMargin(idx.get(RatioCode.OPERATING_MARGIN), new double[]{20, 10, 0}, 5);
        } else {
            // Without enriched data: Net Margin (12) + Gross Margin (8)
            profitScore += scoreMargin(idx.get(RatioCode.NET_MARGIN), new double[]{15, 8, 0}, 12);
            profitScore += scoreMargin(idx.get(RatioCode.GROSS_MARGIN), new double[]{30, 15, 0}, 8);
        }

        // ── Valuation Score (20 pts) ──────────────────────────────
        double valuationScore = 0;
        // PE TTM (12 pts): lower = better
        valuationScore += scorePE(idx.get(RatioCode.PE_TTM), 12);
        // PB (8 pts)
        valuationScore += scorePB(idx.get(RatioCode.PB), 8);

        // ── Solvency Score (15 pts) ── Phase 2A ───────────────────
        double solvencyScore = 0;
        if (hasEnrichedData) {
            // D/E (5 pts): <0.5=5, <1.0=3, <2.0=1
            solvencyScore += scoreDebtToEquity(idx.get(RatioCode.DEBT_TO_EQUITY), 5);
            // Current Ratio (5 pts): >2.0=5, >1.5=3, >1.0=1
            solvencyScore += scoreCurrentRatio(idx.get(RatioCode.CURRENT_RATIO), 5);
            // Interest Coverage (5 pts): >5=5, >3=3, >1=1
            solvencyScore += scoreInterestCoverage(idx.get(RatioCode.INTEREST_COVERAGE), 5);
        }
        // If no enriched data, solvency gets 0 (scored proportionally from other categories in Phase 1 context)

        // ── Cash Flow Score (10 pts) ── Phase 2A ──────────────────
        double cashflowScore = 0;
        if (hasEnrichedData) {
            // CFO > 0 (4 pts)
            BigDecimal cfoToNi = idx.get(RatioCode.CFO_TO_NET_INCOME);
            if (cfoToNi != null && cfoToNi.compareTo(BigDecimal.ZERO) > 0) {
                cashflowScore += 4;
            }
            // FCF > 0 (3 pts)
            BigDecimal fcf = idx.get(RatioCode.FREE_CASH_FLOW);
            if (fcf != null && fcf.compareTo(BigDecimal.ZERO) > 0) {
                cashflowScore += 3;
            }
            // CFO/Net Income > 0.8 (3 pts)
            if (cfoToNi != null && cfoToNi.compareTo(BigDecimal.valueOf(0.8)) > 0) {
                cashflowScore += 3;
            }
        }

        // ── Stability Score (5 pts) ───────────────────────────────
        double stabilityScore = 0;
        BigDecimal positiveNpat = idx.get(RatioCode.POSITIVE_NPAT_LAST_4Q);
        if (positiveNpat != null && positiveNpat.compareTo(BigDecimal.ONE) == 0) {
            stabilityScore = 5; // All 4 quarters positive
        }

        // ── Data Quality Score (10 pts) ───────────────────────────
        long okCount = ratios.stream().filter(r -> r.getQualityStatus() == QualityStatus.OK).count();
        double dataQualityScore = ratios.isEmpty() ? 0 : (10.0 * okCount / ratios.size());

        // ── Overall ───────────────────────────────────────────────
        double overall = growthScore + profitScore + valuationScore +
                solvencyScore + cashflowScore + stabilityScore + dataQualityScore;
        overall = Math.min(100, Math.max(0, overall));

        String rating = toRating(overall);

        List<String> explanationParts = new ArrayList<>();
        if (idx.containsKey(RatioCode.REVENUE_YOY))
            explanationParts.add("Revenue YoY: " + fmt(idx.get(RatioCode.REVENUE_YOY)) + "%");
        if (idx.containsKey(RatioCode.NPAT_YOY))
            explanationParts.add("NPAT YoY: " + fmt(idx.get(RatioCode.NPAT_YOY)) + "%");
        if (idx.containsKey(RatioCode.NET_MARGIN))
            explanationParts.add("Net Margin: " + fmt(idx.get(RatioCode.NET_MARGIN)) + "%");
        if (idx.containsKey(RatioCode.ROE))
            explanationParts.add("ROE: " + fmt(idx.get(RatioCode.ROE)) + "%");
        if (idx.containsKey(RatioCode.PE_TTM))
            explanationParts.add("PE TTM: " + fmt(idx.get(RatioCode.PE_TTM)));
        if (idx.containsKey(RatioCode.DEBT_TO_EQUITY))
            explanationParts.add("D/E: " + fmt(idx.get(RatioCode.DEBT_TO_EQUITY)));
        if (idx.containsKey(RatioCode.CURRENT_RATIO))
            explanationParts.add("Current Ratio: " + fmt(idx.get(RatioCode.CURRENT_RATIO)));
        if (idx.containsKey(RatioCode.FREE_CASH_FLOW))
            explanationParts.add("FCF: " + fmt(idx.get(RatioCode.FREE_CASH_FLOW)));

        return FaScoreSnapshotEntity.builder()
                .ticker(ticker)
                .periodCode(period)
                .growthScore(bd(growthScore))
                .profitabilityScore(bd(profitScore))
                .valuationScore(bd(valuationScore))
                .solvencyScore(bd(solvencyScore))
                .cashflowScore(bd(cashflowScore))
                .stabilityScore(bd(stabilityScore))
                .dataQualityScore(bd(dataQualityScore))
                .overallScore(bd(overall))
                .rating(rating)
                .explanation(String.join("; ", explanationParts))
                .calculationVersion(CALC_VERSION)
                .importBatchId(batchId)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // Scoring helpers
    // ═══════════════════════════════════════════════════════════════

    private double scoreGrowth(BigDecimal yoy, double maxPts) {
        if (yoy == null) return 0;
        double v = yoy.doubleValue();
        if (v > 20) return maxPts;
        if (v > 10) return maxPts * 0.67;
        if (v > 0)  return maxPts * 0.33;
        return 0;
    }

    private double scoreMargin(BigDecimal margin, double[] thresholds, double maxPts) {
        if (margin == null) return 0;
        double v = margin.doubleValue();
        if (v > thresholds[0]) return maxPts;
        if (v > thresholds[1]) return maxPts * 0.6;
        if (v > thresholds[2]) return maxPts * 0.2;
        return 0;
    }

    private double scoreROE(BigDecimal roe, double maxPts) {
        if (roe == null) return 0;
        double v = roe.doubleValue();
        if (v > 20) return maxPts;         // Excellent ROE
        if (v > 15) return maxPts * 0.8;
        if (v > 10) return maxPts * 0.6;
        if (v > 5)  return maxPts * 0.3;
        return 0;
    }

    private double scorePE(BigDecimal pe, double maxPts) {
        if (pe == null) return maxPts * 0.5; // No PE data → neutral
        double v = pe.doubleValue();
        if (v <= 0)  return 0;           // Negative PE = loss
        if (v < 10)  return maxPts;
        if (v < 15)  return maxPts * 0.8;
        if (v < 20)  return maxPts * 0.53;
        if (v < 30)  return maxPts * 0.27;
        return 0;
    }

    private double scorePB(BigDecimal pb, double maxPts) {
        if (pb == null) return maxPts * 0.5;
        double v = pb.doubleValue();
        if (v <= 0)  return 0;
        if (v < 1)   return maxPts;
        if (v < 1.5) return maxPts * 0.8;
        if (v < 2)   return maxPts * 0.6;
        if (v < 3)   return maxPts * 0.3;
        return 0;
    }

    /**
     * Debt to Equity scoring: lower is better.
     * <0.5 = full, <1.0 = 60%, <2.0 = 20%, ≥2.0 = 0
     */
    private double scoreDebtToEquity(BigDecimal de, double maxPts) {
        if (de == null) return 0;
        double v = de.doubleValue();
        if (v < 0)   return 0;           // Negative equity
        if (v < 0.5) return maxPts;
        if (v < 1.0) return maxPts * 0.6;
        if (v < 2.0) return maxPts * 0.2;
        return 0;
    }

    /**
     * Current Ratio scoring: higher is better.
     * >2.0 = full, >1.5 = 60%, >1.0 = 20%, ≤1.0 = 0
     */
    private double scoreCurrentRatio(BigDecimal cr, double maxPts) {
        if (cr == null) return 0;
        double v = cr.doubleValue();
        if (v > 2.0) return maxPts;
        if (v > 1.5) return maxPts * 0.6;
        if (v > 1.0) return maxPts * 0.2;
        return 0;
    }

    /**
     * Interest Coverage scoring: higher is better.
     * >5 = full, >3 = 60%, >1 = 20%, ≤1 = 0
     */
    private double scoreInterestCoverage(BigDecimal ic, double maxPts) {
        if (ic == null) return 0;
        double v = ic.doubleValue();
        if (v > 5) return maxPts;
        if (v > 3) return maxPts * 0.6;
        if (v > 1) return maxPts * 0.2;
        return 0;
    }

    private String toRating(double score) {
        if (score >= 80) return "STRONG_FA";
        if (score >= 65) return "GOOD_FA";
        if (score >= 50) return "FAIR_FA";
        if (score >= 35) return "WEAK_FA";
        return "POOR_FA";
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private String fmt(BigDecimal v) {
        return v == null ? "N/A" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
