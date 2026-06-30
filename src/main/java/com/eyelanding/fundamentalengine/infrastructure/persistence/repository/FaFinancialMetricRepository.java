package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaFinancialMetricRepository extends JpaRepository<FaFinancialMetricEntity, Long> {

    List<FaFinancialMetricEntity> findByTickerAndImportBatchId(String ticker, Long importBatchId);

    List<FaFinancialMetricEntity> findByTickerAndMetricCodeAndImportBatchId(
            String ticker, MetricCode metricCode, Long importBatchId);

    List<FaFinancialMetricEntity> findByMetricCodeAndPeriodCodeAndImportBatchId(
            MetricCode metricCode, String periodCode, Long importBatchId);

    List<FaFinancialMetricEntity> findByImportBatchId(Long importBatchId);

    @Query("SELECT m FROM FaFinancialMetricEntity m WHERE m.ticker = :ticker " +
           "AND m.metricCode = :metricCode AND m.importBatchId = :batchId " +
           "ORDER BY m.periodCode DESC")
    List<FaFinancialMetricEntity> findMetricHistory(
            @Param("ticker") String ticker,
            @Param("metricCode") MetricCode metricCode,
            @Param("batchId") Long batchId);

    Optional<FaFinancialMetricEntity> findByTickerAndPeriodCodeAndMetricCodeAndImportBatchId(
            String ticker, String periodCode, MetricCode metricCode, Long importBatchId);

    List<FaFinancialMetricEntity> findByTickerAndPeriodCodeAndImportBatchId(
            String ticker, String periodCode, Long importBatchId);

    long countByImportBatchIdAndQualityStatus(Long importBatchId, QualityStatus qualityStatus);
}
