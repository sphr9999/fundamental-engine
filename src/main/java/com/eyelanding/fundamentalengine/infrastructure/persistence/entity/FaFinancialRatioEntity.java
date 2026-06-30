package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import com.eyelanding.fundamentalengine.domain.QualityStatus;
import com.eyelanding.fundamentalengine.domain.RatioCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_financial_ratio",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fa_ratio",
                columnNames = {"ticker", "period_code", "ratio_code", "calculation_version", "import_batch_id"}),
        indexes = {
                @Index(name = "idx_fa_ratio_ticker_period", columnList = "ticker,period_code"),
                @Index(name = "idx_fa_ratio_code_period", columnList = "ratio_code,period_code"),
                @Index(name = "idx_fa_ratio_batch", columnList = "import_batch_id")
        })
public class FaFinancialRatioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "period_code", nullable = false, length = 20)
    private String periodCode;

    @Column(name = "ratio_code", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RatioCode ratioCode;

    @Column(name = "ratio_value", precision = 30, scale = 8)
    private BigDecimal ratioValue;

    @Column(name = "quality_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private QualityStatus qualityStatus = QualityStatus.OK;

    @Column(name = "quality_note", columnDefinition = "TEXT")
    private String qualityNote;

    @Column(name = "calculation_version", nullable = false, length = 50)
    private String calculationVersion;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

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
