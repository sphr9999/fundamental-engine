package com.eyelanding.fundamentalengine.application.screener;

import com.eyelanding.fundamentalengine.api.dto.ScreenerResponse;
import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaCompanyEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaScoreSnapshotEntity;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaCompanyRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaImportBatchRepository;
import com.eyelanding.fundamentalengine.infrastructure.persistence.repository.FaScoreSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaScreenerService {

    private final FaScoreSnapshotRepository scoreRepo;
    private final FaCompanyRepository companyRepo;
    private final FaImportBatchRepository batchRepo;

    /**
     * Screen tickers by FA score criteria.
     *
     * @param period      report period (e.g. 2026Q1)
     * @param rating      filter by rating label (STRONG_FA, GOOD_FA, etc.)
     * @param minScore    minimum overall FA score
     * @param exchange    filter by exchange (HOSE, HNX, UPCOM)
     * @param batchId     specific batch (null = latest SUCCESS)
     * @param page        0-indexed page
     * @param pageSize    items per page (max 100)
     */
    public ScreenerResponse screen(String period, String rating, BigDecimal minScore,
                                    String exchange, Long batchId, int page, int pageSize) {
        String effectivePeriod = period != null ? period : "2026Q1";
        Long effectiveBatch = resolveLatestBatch(batchId);
        int safePageSize = Math.min(pageSize, 100);

        PageRequest pageable = PageRequest.of(page, safePageSize,
                Sort.by(Sort.Direction.DESC, "overallScore"));

        String effRating = rating != null && !rating.isBlank() ? rating.toUpperCase() : null;
        String effExchange = exchange != null && !exchange.isBlank() ? exchange : null;

        Page<FaScoreSnapshotEntity> scorePage = scoreRepo.findScreenerScores(
                effectivePeriod, effectiveBatch, effExchange, effRating, minScore, pageable);

        // Preload company info for all tickers in the page
        Map<String, FaCompanyEntity> companyMap = scorePage.getContent().stream()
                .collect(Collectors.toMap(
                        FaScoreSnapshotEntity::getTicker,
                        s -> companyRepo.findByTicker(s.getTicker()).orElseGet(
                                () -> FaCompanyEntity.builder().ticker(s.getTicker()).build()),
                        (a, b) -> a));

        var items = scorePage.getContent().stream()
                .map(s -> {
                    var company = companyMap.getOrDefault(s.getTicker(),
                            FaCompanyEntity.builder().ticker(s.getTicker()).build());
                    return ScreenerResponse.ScreenerItem.builder()
                            .ticker(s.getTicker())
                            .companyName(company.getCompanyName())
                            .exchange(company.getExchange())
                            .industry(company.getIndustryLevel1())
                            .rating(s.getRating())
                            .overallScore(s.getOverallScore())
                            .growthScore(s.getGrowthScore())
                            .profitabilityScore(s.getProfitabilityScore())
                            .valuationScore(s.getValuationScore())
                            .dataQuality("OK") // simplified for Phase 1
                            .build();
                })
                .toList();

        return ScreenerResponse.builder()
                .page(page)
                .pageSize(safePageSize)
                .totalCount(scorePage.getTotalElements())
                .items(items)
                .build();
    }

    private Long resolveLatestBatch(Long batchId) {
        if (batchId != null) return batchId;
        return batchRepo.findTopByStatusOrderByCreatedAtDesc(ImportStatus.SUCCESS)
                .map(b -> b.getId())
                .orElse(batchRepo.findTopByOrderByCreatedAtDesc()
                        .map(b -> b.getId()).orElse(1L));
    }

    private boolean matchExchange(FaCompanyEntity company, String exchange) {
        if (company == null || company.getExchange() == null) return false;
        return company.getExchange().equalsIgnoreCase(exchange);
    }
}
