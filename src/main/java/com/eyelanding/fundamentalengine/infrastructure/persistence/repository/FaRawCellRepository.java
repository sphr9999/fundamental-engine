package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaRawCellEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaRawCellRepository extends JpaRepository<FaRawCellEntity, Long> {
}
