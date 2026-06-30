package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import com.eyelanding.fundamentalengine.domain.ImportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_import_batch", indexes = {
        @Index(name = "idx_fa_import_batch_status", columnList = "status"),
        @Index(name = "idx_fa_import_batch_checksum", columnList = "source_file_checksum"),
        @Index(name = "idx_fa_import_batch_report_period", columnList = "report_period")
})
public class FaImportBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_file_name", nullable = false, columnDefinition = "TEXT")
    private String sourceFileName;

    @Column(name = "source_file_checksum", nullable = false, length = 128)
    private String sourceFileChecksum;

    @Column(name = "report_period", length = 20)
    private String reportPeriod;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ImportStatus status;

    @Column(name = "total_rows")
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "success_rows")
    @Builder.Default
    private Integer successRows = 0;

    @Column(name = "warning_rows")
    @Builder.Default
    private Integer warningRows = 0;

    @Column(name = "error_rows")
    @Builder.Default
    private Integer errorRows = 0;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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
