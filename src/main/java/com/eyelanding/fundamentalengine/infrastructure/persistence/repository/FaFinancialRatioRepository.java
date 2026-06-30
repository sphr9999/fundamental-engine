package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaFinancialRatioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaFinancialRatioRepository extends JpaRepository<FaFinancialRatioEntity, Long> {

    List<FaFinancialRatioEntity> findByTickerAndImportBatchId(String ticker, Long importBatchId);

    List<FaFinancialRatioEntity> findByTickerAndPeriodCodeAndImportBatchId(
            String ticker, String periodCode, Long importBatchId);

    void deleteByImportBatchId(Long importBatchId);
}
