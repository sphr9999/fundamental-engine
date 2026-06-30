package com.eyelanding.fundamentalengine.domain;

/**
 * Ratio codes for calculated financial ratios.
 * <p>
 * Phase 1 ratios use Income Statement + Market Data.
 * Phase 2A ratios additionally use Balance Sheet + Cash Flow data.
 */
public enum RatioCode {

    // ── Phase 1: Growth ──
    REVENUE_YOY,
    NPAT_YOY,
    REVENUE_QOQ,
    NPAT_QOQ,

    // ── Phase 1: Profitability ──
    GROSS_MARGIN,
    NET_MARGIN,

    // ── Phase 1: Valuation & Market ──
    MARKET_CAP,
    EPS_TTM,
    PE_TTM,
    PB,

    // ── Phase 1: Stability ──
    POSITIVE_NPAT_LAST_4Q,
    PROFIT_TURNAROUND_FLAG,

    // ── Phase 2A: Profitability (enriched) ──
    ROE,
    ROA,
    ROIC,
    OPERATING_MARGIN,
    EBITDA_MARGIN,

    // ── Phase 2A: Solvency ──
    DEBT_TO_EQUITY,
    NET_DEBT,
    NET_DEBT_TO_EBITDA,
    CURRENT_RATIO,
    QUICK_RATIO,
    INTEREST_COVERAGE,

    // ── Phase 2A: Efficiency ──
    ASSET_TURNOVER,
    INVENTORY_DAYS,
    RECEIVABLE_DAYS,

    // ── Phase 2A: Cash Flow ──
    FREE_CASH_FLOW,
    CFO_TO_NET_INCOME,
    FREE_CASH_FLOW_YIELD,
    PRICE_TO_CFO,
    CAPEX_TO_REVENUE,

    // ── Phase 2A: Per Share ──
    BOOK_VALUE_PER_SHARE
}
