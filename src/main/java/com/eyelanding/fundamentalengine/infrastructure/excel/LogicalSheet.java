package com.eyelanding.fundamentalengine.infrastructure.excel;

/**
 * Logical sheet identifiers mapped from Vietnamese Excel sheet names.
 * Sheet aliases are resolved by ExcelSheetAliasResolver.
 */
public enum LogicalSheet {
    REVENUE,
    NPAT,
    GROSS_PROFIT,
    NPAT_YEARLY,
    EPS_DILUTED,
    SHARES_OUTSTANDING,
    STOCK_PRICE,
    PB,
    COMPANY_LIST,
    FILTER,
    UNKNOWN
}
