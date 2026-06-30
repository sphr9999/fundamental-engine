package com.eyelanding.fundamentalengine.infrastructure.vci.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parsed representation of a single financial line item from the VCI API.
 *
 * <p>The VCI {@code /v1/company/{ticker}/financial-statement} endpoint returns
 * a JSON wrapper with {@code data.years[]} and {@code data.quarters[]} arrays.
 * Each record contains coded field names (e.g. {@code isa3}, {@code bsa60})
 * with numeric values.</p>
 *
 * <p>This DTO captures a single field across multiple periods after
 * {@link VciResponseParser} transforms the VCI response.</p>
 *
 * @see VciResponseParser
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VciFinancialItem {

    /**
     * Vietnamese display name of the line item (e.g. "Doanh thu thuần").
     */
    private String name;

    /**
     * English/normalized field name used by VCI (e.g. "net_sales").
     * This is the key used for mapping to internal {@code MetricCode}.
     */
    private String fieldName;

    /**
     * Unique item identifier. Often identical to or derived from
     * {@link #fieldName}, but may differ for sub-items.
     */
    private String itemId;

    /**
     * Period-keyed numeric values.
     *
     * <p>Keys follow VCI's period format: {@code "2026-Q1"}, {@code "2025-Q4"},
     * {@code "2025-Y"}, or {@code "2025"}. Values are the raw numeric amounts
     * as reported (units depend on the statement — typically VND).</p>
     *
     * <p>A {@code null} value in the map indicates the field was present in the
     * response but had a null/non-numeric value for that period.</p>
     */
    private Map<String, BigDecimal> periodValues;
}
