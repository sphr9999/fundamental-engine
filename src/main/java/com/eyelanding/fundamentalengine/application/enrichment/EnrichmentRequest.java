package com.eyelanding.fundamentalengine.application.enrichment;

import java.util.List;

/**
 * Request model for triggering data enrichment from VCI API.
 */
public record EnrichmentRequest(
    List<String> tickers,           // null = all active tickers in dim_company
    List<String> exchanges,         // filter by exchange: HOSE, HNX, UPCOM
    List<String> reportTypes,       // INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW
    String period,                  // "quarter" or "year"
    String importedBy               // who triggered the enrichment
) {
    public static final String REPORT_INCOME_STATEMENT = "INCOME_STATEMENT";
    public static final String REPORT_BALANCE_SHEET = "BALANCE_SHEET";
    public static final String REPORT_CASH_FLOW = "CASH_FLOW";

    public static final List<String> ALL_REPORT_TYPES = List.of(
            REPORT_INCOME_STATEMENT, REPORT_BALANCE_SHEET, REPORT_CASH_FLOW);

    public List<String> effectiveReportTypes() {
        return (reportTypes == null || reportTypes.isEmpty()) ? ALL_REPORT_TYPES : reportTypes;
    }

    public String effectivePeriod() {
        return (period == null || period.isBlank()) ? "quarter" : period;
    }
}
