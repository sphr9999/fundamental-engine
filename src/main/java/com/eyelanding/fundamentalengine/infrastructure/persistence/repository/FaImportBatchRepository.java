package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.domain.ImportStatus;
import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaImportBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaImportBatchRepository extends JpaRepository<FaImportBatchEntity, Long> {

    List<FaImportBatchEntity> findByStatusOrderByCreatedAtDesc(ImportStatus status);

    Optional<FaImportBatchEntity> findTopByStatusOrderByCreatedAtDesc(ImportStatus status);

    Optional<FaImportBatchEntity> findTopByOrderByCreatedAtDesc();

    List<FaImportBatchEntity> findByReportPeriodOrderByCreatedAtDesc(String reportPeriod);
}
