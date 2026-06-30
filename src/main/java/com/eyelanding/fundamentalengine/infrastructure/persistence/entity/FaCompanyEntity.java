package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import com.eyelanding.fundamentalengine.domain.SectorModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dim_company", indexes = {
        @Index(name = "idx_dim_company_exchange", columnList = "exchange"),
        @Index(name = "idx_dim_company_sector_model", columnList = "sector_model")
})
public class FaCompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(name = "company_name", columnDefinition = "TEXT")
    private String companyName;

    @Column(name = "exchange", length = 20)
    private String exchange;

    @Column(name = "industry_level_1", columnDefinition = "TEXT")
    private String industryLevel1;

    @Column(name = "industry_level_2", columnDefinition = "TEXT")
    private String industryLevel2;

    @Column(name = "industry_level_3", columnDefinition = "TEXT")
    private String industryLevel3;

    @Column(name = "sector_model", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SectorModel sectorModel = SectorModel.UNKNOWN;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
