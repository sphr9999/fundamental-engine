package com.eyelanding.fundamentalengine.infrastructure.vci.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Parsed company overview from VCI API {@code GET /v1/company/{ticker}}.
 *
 * <p>Contains realtime market data: current price, market cap, shares,
 * analyst target price and rating, sector classification, and 52-week range.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VciCompanyInfo {

    private String ticker;

    /** Realtime stock price (VND). */
    private BigDecimal currentPrice;

    /** Realtime market capitalisation (VND). */
    private BigDecimal marketCap;

    /** Number of shares used in market-cap calculation. */
    private BigDecimal numberOfSharesMktCap;

    /** Analyst consensus target price (VND). */
    private BigDecimal targetPrice;

    /** Analyst rating: BUY, HOLD, SELL, etc. */
    private String rating;

    /** Rating effective date string, e.g. "12-May-26". */
    private String ratingAsOf;

    /** English sector name. */
    private String sector;

    /** Vietnamese sector name. */
    private String sectorVn;

    /** 52-week high. */
    private BigDecimal highestPrice1Year;

    /** 52-week low. */
    private BigDecimal lowestPrice1Year;

    /** Foreign ownership percentage (0.0 - 1.0). */
    private BigDecimal foreignerPercentage;

    /** Maximum allowed foreign ownership (0.0 - 1.0). */
    private BigDecimal maximumForeignPercentage;

    /** Whether the company is a bank. */
    private boolean isBank;
}
