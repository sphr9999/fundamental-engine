package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_data_quality_issue", indexes = {
        @Index(name = "idx_fa_quality_batch", columnList = "import_batch_id"),
        @Index(name = "idx_fa_quality_ticker", columnList = "ticker"),
        @Index(name = "idx_fa_quality_severity", columnList = "severity")
})
public class FaDataQualityIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(name = "ticker", length = 20)
    private String ticker;

    @Column(name = "period_code", length = 20)
    private String periodCode;

    @Column(name = "metric_code", length = 50)
    private String metricCode;

    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    /**
     * Severity levels: ERROR, WARN, INFO
     */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "source_sheet", length = 200)
    private String sourceSheet;

    @Column(name = "source_cell", length = 30)
    private String sourceCell;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
