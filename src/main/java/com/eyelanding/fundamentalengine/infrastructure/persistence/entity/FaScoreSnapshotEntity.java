package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_score_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fa_score",
                columnNames = {"ticker", "period_code", "calculation_version", "import_batch_id"}),
        indexes = {
                @Index(name = "idx_fa_score_ticker_period", columnList = "ticker,period_code"),
                @Index(name = "idx_fa_score_overall", columnList = "period_code,overall_score"),
                @Index(name = "idx_fa_score_rating", columnList = "period_code,rating")
        })
public class FaScoreSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "period_code", nullable = false, length = 20)
    private String periodCode;

    @Column(name = "growth_score", precision = 10, scale = 2)
    private BigDecimal growthScore;

    @Column(name = "profitability_score", precision = 10, scale = 2)
    private BigDecimal profitabilityScore;

    @Column(name = "valuation_score", precision = 10, scale = 2)
    private BigDecimal valuationScore;

    @Column(name = "stability_score", precision = 10, scale = 2)
    private BigDecimal stabilityScore;

    @Column(name = "data_quality_score", precision = 10, scale = 2)
    private BigDecimal dataQualityScore;

    @Column(name = "solvency_score", precision = 10, scale = 2)
    private BigDecimal solvencyScore;

    @Column(name = "cashflow_score", precision = 10, scale = 2)
    private BigDecimal cashflowScore;

    @Column(name = "overall_score", precision = 10, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "rating", length = 30)
    private String rating;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "calculation_version", nullable = false, length = 50)
    private String calculationVersion;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
