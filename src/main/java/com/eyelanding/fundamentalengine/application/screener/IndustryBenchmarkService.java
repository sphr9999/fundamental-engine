package com.eyelanding.fundamentalengine.application.screener;

import com.eyelanding.fundamentalengine.api.dto.IndustryBenchmarkResponse;
import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.domain.RatioCode;
import com.eyelanding.fundamentalengine.infrastructure.config.RedisCacheConfig;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaCompanyEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialRatioEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaScoreSnapshotEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaCompanyRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaFinancialRatioRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaImportBatchRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaScoreSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates median financial ratios grouped by industry (ngành).
 * Uses existing fa_financial_ratio + dim_company data.
 */
@Service
@RequiredArgsConstructor
public class IndustryBenchmarkService {

    private final FaCompanyRepository companyRepo;
    private final FaFinancialRatioRepository ratioRepo;
    private final FaScoreSnapshotRepository scoreRepo;
    private final FaImportBatchRepository batchRepo;

    @Cacheable(value = RedisCacheConfig.CACHE_INDUSTRY_BENCHMARK, key = "#period + ':' + #batchId")
    public IndustryBenchmarkResponse getBenchmarks(String period, Long batchId) {
        String effectivePeriod = period != null ? period : "2026Q1";
        Long effectiveBatch = resolveLatestBatch(batchId);

        // Load all companies with industry info
        Map<String, String> tickerToIndustry = companyRepo.findAll().stream()
                .filter(c -> c.getIndustryLevel1() != null && !c.getIndustryLevel1().isBlank())
                .collect(Collectors.toMap(
                        FaCompanyEntity::getTicker,
                        FaCompanyEntity::getIndustryLevel1,
                        (a, b) -> a));

        // Load all ratios for this period/batch
        List<FaFinancialRatioEntity> allRatios = ratioRepo.findAll().stream()
                .filter(r -> r.getImportBatchId().equals(effectiveBatch)
                        && r.getPeriodCode().equals(effectivePeriod)
                        && r.getQualityStatus() == QualityStatus.OK
                        && r.getRatioValue() != null)
                .toList();

        // Load FA scores for this period/batch
        Map<String, BigDecimal> tickerScores = scoreRepo.findAll().stream()
                .filter(s -> s.getImportBatchId().equals(effectiveBatch)
                        && s.getPeriodCode().equals(effectivePeriod)
                        && s.getOverallScore() != null)
                .collect(Collectors.toMap(
                        FaScoreSnapshotEntity::getTicker,
                        FaScoreSnapshotEntity::getOverallScore,
                        (a, b) -> a));

        // Group ratios by industry
        Map<String, Map<RatioCode, List<BigDecimal>>> byIndustry = new LinkedHashMap<>();
        Map<String, Set<String>> industryTickers = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> industryScores = new LinkedHashMap<>();

        for (var ratio : allRatios) {
            String industry = tickerToIndustry.get(ratio.getTicker());
            if (industry == null) continue;

            byIndustry.computeIfAbsent(industry, k -> new HashMap<>())
                    .computeIfAbsent(ratio.getRatioCode(), k -> new ArrayList<>())
                    .add(ratio.getRatioValue());

            industryTickers.computeIfAbsent(industry, k -> new HashSet<>())
                    .add(ratio.getTicker());
        }

        // Collect scores by industry
        for (var entry : tickerScores.entrySet()) {
            String industry = tickerToIndustry.get(entry.getKey());
            if (industry == null) continue;
            industryScores.computeIfAbsent(industry, k -> new ArrayList<>()).add(entry.getValue());
        }

        // Build benchmark per industry
        List<IndustryBenchmarkResponse.IndustryBenchmark> benchmarks = new ArrayList<>();
        for (var industryEntry : byIndustry.entrySet()) {
            String industry = industryEntry.getKey();
            Map<RatioCode, List<BigDecimal>> ratioMap = industryEntry.getValue();

            benchmarks.add(IndustryBenchmarkResponse.IndustryBenchmark.builder()
                    .industry(industry)
                    .tickerCount(industryTickers.getOrDefault(industry, Set.of()).size())
                    .medianPe(median(ratioMap.get(RatioCode.PE_TTM)))
                    .medianPb(median(ratioMap.get(RatioCode.PB)))
                    .medianNetMargin(median(ratioMap.get(RatioCode.NET_MARGIN)))
                    .medianGrossMargin(median(ratioMap.get(RatioCode.GROSS_MARGIN)))
                    .medianRevenueYoy(median(ratioMap.get(RatioCode.REVENUE_YOY)))
                    .medianNpatYoy(median(ratioMap.get(RatioCode.NPAT_YOY)))
                    .medianFaScore(median(industryScores.get(industry)))
                    .build());
        }

        // Sort by ticker count descending
        benchmarks.sort(Comparator.comparingInt(IndustryBenchmarkResponse.IndustryBenchmark::getTickerCount).reversed());

        return IndustryBenchmarkResponse.builder()
                .period(effectivePeriod)
                .industryCount(benchmarks.size())
                .benchmarks(benchmarks)
                .build();
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2).setScale(4, RoundingMode.HALF_UP);
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private Long resolveLatestBatch(Long batchId) {
        if (batchId != null) return batchId;
        return batchRepo.findTopByStatusOrderByCreatedAtDesc(ImportStatus.SUCCESS)
                .map(b -> b.getId())
                .orElse(batchRepo.findTopByOrderByCreatedAtDesc()
                        .map(b -> b.getId()).orElse(1L));
    }
}
