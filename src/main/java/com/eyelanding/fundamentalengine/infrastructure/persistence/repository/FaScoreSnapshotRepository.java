package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaScoreSnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaScoreSnapshotRepository extends JpaRepository<FaScoreSnapshotEntity, Long> {

    Optional<FaScoreSnapshotEntity> findByTickerAndPeriodCodeAndImportBatchId(
            String ticker, String periodCode, Long importBatchId);

    Optional<FaScoreSnapshotEntity> findTopByTickerAndPeriodCodeAndImportBatchIdOrderByCreatedAtDesc(
            String ticker, String periodCode, Long importBatchId);

    List<FaScoreSnapshotEntity> findByTickerAndImportBatchId(String ticker, Long importBatchId);

    Page<FaScoreSnapshotEntity> findByPeriodCodeAndImportBatchIdAndOverallScoreGreaterThanEqual(
            String periodCode, Long importBatchId, BigDecimal minScore, Pageable pageable);

    Page<FaScoreSnapshotEntity> findByPeriodCodeAndImportBatchId(
            String periodCode, Long importBatchId, Pageable pageable);

    Page<FaScoreSnapshotEntity> findByPeriodCodeAndImportBatchIdAndRating(
            String periodCode, Long importBatchId, String rating, Pageable pageable);

    @Query("SELECT s FROM FaScoreSnapshotEntity s JOIN FaCompanyEntity c ON s.ticker = c.ticker " +
           "WHERE s.periodCode = :periodCode AND s.importBatchId = :importBatchId " +
           "AND (:exchange IS NULL OR c.exchange = :exchange) " +
           "AND (:rating IS NULL OR s.rating = :rating) " +
           "AND (:minScore IS NULL OR s.overallScore >= :minScore)")
    Page<FaScoreSnapshotEntity> findScreenerScores(
            @Param("periodCode") String periodCode,
            @Param("importBatchId") Long importBatchId,
            @Param("exchange") String exchange,
            @Param("rating") String rating,
            @Param("minScore") BigDecimal minScore,
            Pageable pageable);
}
