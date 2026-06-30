package com.eyelanding.fundamentalengine.domain;

/**
 * Metric codes for raw financial metrics.
 * <p>
 * Phase 1 metrics are imported from Excel workbook.
 * Phase 2A metrics are enriched from VCI (VietCap) API.
 */
public enum MetricCode {

    // ── Phase 1: Excel Import (Income Statement partial + Market Data) ──
    REVENUE,
    NPAT,
    GROSS_PROFIT,
    NPAT_YEARLY,
    EPS_DILUTED,
    SHARES_OUTSTANDING,
    CLOSE_PRICE,
    PB,

    // ── Phase 2A: Income Statement (enriched from VCI API) ──
    REVENUE_GROSS,
    COST_OF_SALES,
    OPERATING_PROFIT,
    PROFIT_BEFORE_TAX,
    NPAT_PARENT,
    EPS_BASIC,
    FINANCIAL_INCOME,
    FINANCIAL_EXPENSES,
    INTEREST_EXPENSES,
    SELLING_EXPENSES,
    ADMIN_EXPENSES,

    // ── Phase 2A: Balance Sheet ──
    TOTAL_ASSETS,
    CURRENT_ASSETS,
    CASH_AND_EQUIVALENTS,
    SHORT_TERM_INVESTMENTS,
    ACCOUNTS_RECEIVABLE,
    INVENTORY,
    FIXED_ASSETS_NET,
    LONG_TERM_INVESTMENTS,
    TOTAL_LIABILITIES,
    CURRENT_LIABILITIES,
    SHORT_TERM_DEBT,
    ACCOUNTS_PAYABLE,
    LONG_TERM_LIABILITIES,
    LONG_TERM_DEBT,
    TOTAL_EQUITY,
    CHARTER_CAPITAL,
    RETAINED_EARNINGS,
    CONSTRUCTION_IN_PROGRESS,
    GOODWILL,
    MINORITY_INTERESTS,

    // ── Phase 2A: Cash Flow Statement ──
    CFO,
    CFI,
    CFF,
    CAPEX,
    DEPRECIATION_AMORTIZATION,
    DIVIDENDS_PAID,
    INTEREST_PAID,
    NET_CASH_CHANGE
}
