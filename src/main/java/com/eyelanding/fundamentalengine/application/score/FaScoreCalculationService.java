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
 * Score breakdown (100 pts total):
 * ┌─────────────────────┬────────┐
 * │ Growth              │ 30 pts │  Revenue YoY + NPAT YoY
 * │ Profitability       │ 25 pts │  Net Margin + Gross Margin
 * │ Valuation           │ 25 pts │  PE, PB (lower = higher score)
 * │ Stability           │ 10 pts │  Positive NPAT last 4Q
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

    private static final String CALC_VERSION = "v1.0";

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

        // ── Growth Score (30 pts) ──────────────────────────────────
        double growthScore = 0;
        // Revenue YoY (15 pts): >20%=15, >10%=10, >0%=5, ≤0%=0
        growthScore += scoreGrowth(idx.get(RatioCode.REVENUE_YOY), 15);
        // NPAT YoY (15 pts): same thresholds
        growthScore += scoreGrowth(idx.get(RatioCode.NPAT_YOY), 15);

        // ── Profitability Score (25 pts) ──────────────────────────
        double profitScore = 0;
        // Net Margin (15 pts): >15%=15, >8%=10, >0%=5, ≤0%=0
        profitScore += scoreMargin(idx.get(RatioCode.NET_MARGIN), new double[]{15, 8, 0}, 15);
        // Gross Margin (10 pts): >30%=10, >15%=6, >0%=3, ≤0%=0
        profitScore += scoreMargin(idx.get(RatioCode.GROSS_MARGIN), new double[]{30, 15, 0}, 10);

        // ── Valuation Score (25 pts) ──────────────────────────────
        double valuationScore = 0;
        // PE TTM (15 pts): lower = better; <10=15, <15=12, <20=8, <30=4, >30=0
        valuationScore += scorePE(idx.get(RatioCode.PE_TTM), 15);
        // PB (10 pts): <1=10, <1.5=8, <2=6, <3=3, >3=0
        valuationScore += scorePB(idx.get(RatioCode.PB), 10);

        // ── Stability Score (10 pts) ──────────────────────────────
        double stabilityScore = 0;
        BigDecimal positiveNpat = idx.get(RatioCode.POSITIVE_NPAT_LAST_4Q);
        if (positiveNpat != null && positiveNpat.compareTo(BigDecimal.ONE) == 0) {
            stabilityScore = 10; // All 4 quarters positive
        }

        // ── Data Quality Score (10 pts) ───────────────────────────
        long okCount = ratios.stream().filter(r -> r.getQualityStatus() == QualityStatus.OK).count();
        double dataQualityScore = ratios.isEmpty() ? 0 : (10.0 * okCount / ratios.size());

        // ── Overall ───────────────────────────────────────────────
        double overall = growthScore + profitScore + valuationScore + stabilityScore + dataQualityScore;
        overall = Math.min(100, Math.max(0, overall));

        String rating = toRating(overall);

        List<String> explanationParts = new ArrayList<>();
        if (idx.containsKey(RatioCode.REVENUE_YOY))
            explanationParts.add("Revenue YoY: " + fmt(idx.get(RatioCode.REVENUE_YOY)) + "%");
        if (idx.containsKey(RatioCode.NPAT_YOY))
            explanationParts.add("NPAT YoY: " + fmt(idx.get(RatioCode.NPAT_YOY)) + "%");
        if (idx.containsKey(RatioCode.NET_MARGIN))
            explanationParts.add("Net Margin: " + fmt(idx.get(RatioCode.NET_MARGIN)) + "%");
        if (idx.containsKey(RatioCode.PE_TTM))
            explanationParts.add("PE TTM: " + fmt(idx.get(RatioCode.PE_TTM)));

        return FaScoreSnapshotEntity.builder()
                .ticker(ticker)
                .periodCode(period)
                .growthScore(bd(growthScore))
                .profitabilityScore(bd(profitScore))
                .valuationScore(bd(valuationScore))
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
