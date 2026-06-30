package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.PeriodType;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_financial_metric",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fa_metric",
                columnNames = {"ticker", "period_type", "period_code", "metric_code", "import_batch_id"}),
        indexes = {
                @Index(name = "idx_fa_metric_ticker_period", columnList = "ticker,period_code"),
                @Index(name = "idx_fa_metric_metric_period", columnList = "metric_code,period_code"),
                @Index(name = "idx_fa_metric_batch", columnList = "import_batch_id"),
                @Index(name = "idx_fa_metric_quality", columnList = "quality_status")
        })
public class FaFinancialMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "period_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;

    @Column(name = "period_code", nullable = false, length = 20)
    private String periodCode;

    @Column(name = "metric_code", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MetricCode metricCode;

    @Column(name = "metric_value", precision = 30, scale = 6)
    private BigDecimal metricValue;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "quality_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private QualityStatus qualityStatus = QualityStatus.OK;

    @Column(name = "quality_note", columnDefinition = "TEXT")
    private String qualityNote;

    @Column(name = "source_sheet", length = 200)
    private String sourceSheet;

    @Column(name = "source_cell", length = 30)
    private String sourceCell;

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
