package com.eyelanding.fundamentalengine.infrastructure.vps;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Realtime stock data from VPS {@code bgapidatafeed} API.
 *
 * <p>Prices are in VND (already multiplied by 1000 from the raw API
 * which returns prices in thousands).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpsStockData {

    private String ticker;

    /** Last matched price (VND). */
    private BigDecimal lastPrice;

    /** Reference price / previous close (VND). */
    private BigDecimal referencePrice;

    /** Ceiling price (VND). */
    private BigDecimal ceilingPrice;

    /** Floor price (VND). */
    private BigDecimal floorPrice;

    /** Session high (VND). */
    private BigDecimal highPrice;

    /** Session low (VND). */
    private BigDecimal lowPrice;

    /** Volume-weighted average price (VND). */
    private BigDecimal averagePrice;

    /** Total matched volume (shares). */
    private Long totalVolume;

    /** Change percentage (e.g. 1.48 = +1.48%). */
    private BigDecimal changePercent;

    /** Price source indicator. */
    public static final String SOURCE = "VPS_REALTIME";
}
