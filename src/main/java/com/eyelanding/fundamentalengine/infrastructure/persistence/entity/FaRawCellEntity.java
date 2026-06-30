package com.eyelanding.fundamentalengine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Optional raw cell evidence table — used for debugging import issues.
 * Not all cells are stored here, only problematic ones.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fa_raw_cell", indexes = {
        @Index(name = "idx_fa_raw_cell_batch_sheet", columnList = "import_batch_id,sheet_name"),
        @Index(name = "idx_fa_raw_cell_ref", columnList = "import_batch_id,sheet_name,cell_ref")
})
public class FaRawCellEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(name = "sheet_name", nullable = false, length = 200)
    private String sheetName;

    @Column(name = "cell_ref", nullable = false, length = 30)
    private String cellRef;

    @Column(name = "row_index", nullable = false)
    private Integer rowIndex;

    @Column(name = "col_index", nullable = false)
    private Integer colIndex;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "numeric_value", precision = 30, scale = 6)
    private BigDecimal numericValue;

    @Column(name = "formula_text", columnDefinition = "TEXT")
    private String formulaText;

    @Column(name = "cell_type", length = 30)
    private String cellType;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
