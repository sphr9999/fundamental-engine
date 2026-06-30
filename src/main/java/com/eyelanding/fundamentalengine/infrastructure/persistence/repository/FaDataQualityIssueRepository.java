package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaDataQualityIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaDataQualityIssueRepository extends JpaRepository<FaDataQualityIssueEntity, Long> {

    List<FaDataQualityIssueEntity> findByImportBatchIdOrderByCreatedAtAsc(Long importBatchId);

    List<FaDataQualityIssueEntity> findByImportBatchIdAndSeverityOrderByCreatedAtAsc(
            Long importBatchId, String severity);

    long countByImportBatchIdAndSeverity(Long importBatchId, String severity);
}
