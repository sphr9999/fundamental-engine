package com.eyelanding.fundamentalengine.infrastructure.persistence.repository;

import com.eyelanding.fundamentalengine.infrastructure.persistence.entity.FaCompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaCompanyRepository extends JpaRepository<FaCompanyEntity, Long> {

    Optional<FaCompanyEntity> findByTicker(String ticker);

    List<FaCompanyEntity> findByExchange(String exchange);

    boolean existsByTicker(String ticker);

    List<FaCompanyEntity> findByIsActiveTrue();

    List<FaCompanyEntity> findByExchangeInAndIsActiveTrue(List<String> exchanges);
}
