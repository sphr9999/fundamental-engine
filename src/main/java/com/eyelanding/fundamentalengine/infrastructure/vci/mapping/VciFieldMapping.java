package com.eyelanding.fundamentalengine.infrastructure.vci.mapping;

import com.eyelanding.fundamentalengine.domain.MetricCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps VCI financial statement field codes (isa1, bsa1, cfa1, etc.)
 * to our internal {@link MetricCode} enum values.
 *
 * <p>VCI uses coded field names following Vietnamese Accounting Standard (TT200):
 * <ul>
 *   <li>ISA = Income Statement section A (Revenue → Operating Profit)</li>
 *   <li>BSA = Balance Sheet section A (Assets → Equity)</li>
 *   <li>CFA = Cash Flow section A (Operating → Investing → Financing)</li>
 * </ul>
 *
 * <p>Field codes were reverse-engineered from VCI API responses and cross-referenced
 * with known HPG Q1.2026 financial data from Excel import.</p>
 */
public final class VciFieldMapping {

    private VciFieldMapping() {}

    // ═══════════════════════════════════════════════════════════════
    // Income Statement mapping (ISA prefix)
    // Based on TT200 Báo cáo KQKD
    // ═══════════════════════════════════════════════════════════════
    private static final Map<String, MetricCode> INCOME_STATEMENT_MAP = new LinkedHashMap<>();
    static {
        INCOME_STATEMENT_MAP.put("isa1", MetricCode.REVENUE_GROSS);        // Doanh thu bán hàng
        // isa2 = Các khoản giảm trừ DT (deductions) — skip
        INCOME_STATEMENT_MAP.put("isa3", MetricCode.REVENUE);              // Doanh thu thuần
        INCOME_STATEMENT_MAP.put("isa4", MetricCode.COST_OF_SALES);        // Giá vốn hàng bán
        INCOME_STATEMENT_MAP.put("isa5", MetricCode.GROSS_PROFIT);         // Lợi nhuận gộp
        INCOME_STATEMENT_MAP.put("isa6", MetricCode.FINANCIAL_INCOME);     // Doanh thu hoạt động tài chính
        INCOME_STATEMENT_MAP.put("isa7", MetricCode.FINANCIAL_EXPENSES);   // Chi phí tài chính
        INCOME_STATEMENT_MAP.put("isa8", MetricCode.INTEREST_EXPENSES);    // Trong đó: CP lãi vay
        INCOME_STATEMENT_MAP.put("isa9", MetricCode.SELLING_EXPENSES);     // Chi phí bán hàng
        INCOME_STATEMENT_MAP.put("isa10", MetricCode.ADMIN_EXPENSES);      // Chi phí quản lý DN
        INCOME_STATEMENT_MAP.put("isa11", MetricCode.OPERATING_PROFIT);    // LN thuần từ HĐKD
        // isa12 = Thu nhập khác — skip
        // isa13 = Chi phí khác — skip
        // isa14 = LN khác — skip
        // isa15 = Phần LN/lỗ từ CTy liên kết — skip
        INCOME_STATEMENT_MAP.put("isa16", MetricCode.PROFIT_BEFORE_TAX);   // Tổng LN kế toán trước thuế
        // isa17 = CP thuế TNDN hiện hành — skip
        // isa18 = CP thuế TNDN hoãn lại — skip
        // isa19 = Tổng CP thuế TNDN — skip
        INCOME_STATEMENT_MAP.put("isa20", MetricCode.NPAT);                // LNST
        // isa21 = LN sau thuế của cổ đông thiểu số — skip
        INCOME_STATEMENT_MAP.put("isa22", MetricCode.NPAT_PARENT);         // LNST của CĐ cty mẹ
    }

    // ═══════════════════════════════════════════════════════════════
    // Balance Sheet mapping (BSA prefix)
    // Based on TT200 Bảng CĐKT — verified against HPG Q1.2026 actual data
    // ═══════════════════════════════════════════════════════════════
    private static final Map<String, MetricCode> BALANCE_SHEET_MAP = new LinkedHashMap<>();
    static {
        // A. TÀI SẢN NGẮN HẠN (Current Assets)
        BALANCE_SHEET_MAP.put("bsa1", MetricCode.CURRENT_ASSETS);           // 104.37T
        BALANCE_SHEET_MAP.put("bsa2", MetricCode.CASH_AND_EQUIVALENTS);     // 11.46T
        BALANCE_SHEET_MAP.put("bsa10", MetricCode.SHORT_TERM_INVESTMENTS);  // 3.19T
        BALANCE_SHEET_MAP.put("bsa15", MetricCode.ACCOUNTS_RECEIVABLE);     // 43.52T (Phải thu NH)
        BALANCE_SHEET_MAP.put("bsa18", MetricCode.INVENTORY);               // 7.99T (Hàng tồn kho)

        // B. TÀI SẢN DÀI HẠN (Non-current Assets)
        BALANCE_SHEET_MAP.put("bsa23", null);                                // 154.96T Tổng TS dài hạn (skip, use bsa53)
        BALANCE_SHEET_MAP.put("bsa29", MetricCode.FIXED_ASSETS_NET);        // 134.57T TSCĐ
        BALANCE_SHEET_MAP.put("bsa43", MetricCode.CONSTRUCTION_IN_PROGRESS);// 1.86T XDCB dở dang
        BALANCE_SHEET_MAP.put("bsa49", MetricCode.LONG_TERM_INVESTMENTS);   // 6.55T Đầu tư TC dài hạn
        BALANCE_SHEET_MAP.put("bsa50", MetricCode.GOODWILL);                // 5.91T Lợi thế thương mại

        // TỔNG TÀI SẢN
        BALANCE_SHEET_MAP.put("bsa53", MetricCode.TOTAL_ASSETS);            // 259.33T ✅ verified

        // C. NỢ PHẢI TRẢ (Liabilities)
        BALANCE_SHEET_MAP.put("bsa54", MetricCode.TOTAL_LIABILITIES);       // 119.55T ✅ verified
        BALANCE_SHEET_MAP.put("bsa55", MetricCode.CURRENT_LIABILITIES);     // 86.37T  ✅ verified
        BALANCE_SHEET_MAP.put("bsa56", MetricCode.SHORT_TERM_DEBT);         // 62.80T  Vay ngắn hạn
        BALANCE_SHEET_MAP.put("bsa67", MetricCode.ACCOUNTS_PAYABLE);        // 33.18T  Phải trả người bán
        BALANCE_SHEET_MAP.put("bsa80", MetricCode.LONG_TERM_LIABILITIES);   // 76.75T  Nợ dài hạn
        BALANCE_SHEET_MAP.put("bsa79", MetricCode.LONG_TERM_DEBT);          // 76.75T  Vay dài hạn

        // D. VỐN CHỦ SỞ HỮU (Equity)
        BALANCE_SHEET_MAP.put("bsa90", MetricCode.TOTAL_EQUITY);            // 59.89T  ✅ verified
        BALANCE_SHEET_MAP.put("bsa57", MetricCode.CHARTER_CAPITAL);         // 17.16T  Vốn góp CSH
        BALANCE_SHEET_MAP.put("bsa59", MetricCode.RETAINED_EARNINGS);       // 1.91T   LNST chưa phân phối
        BALANCE_SHEET_MAP.put("bsa86", MetricCode.MINORITY_INTERESTS);      // 1.39T   Lợi ích CĐ thiểu số
    }

    // ═══════════════════════════════════════════════════════════════
    // Cash Flow mapping (CFA prefix)
    // Based on TT200 Báo cáo LCTT — verified against HPG Q1.2026 actual data
    // ═══════════════════════════════════════════════════════════════
    private static final Map<String, MetricCode> CASH_FLOW_MAP = new LinkedHashMap<>();
    static {
        // cfa1 = 10.76T → LNTT (starting point of indirect method, NOT NPAT — skip)
        CASH_FLOW_MAP.put("cfa2", MetricCode.DEPRECIATION_AMORTIZATION);    // 2.85T Khấu hao
        CASH_FLOW_MAP.put("cfa18", MetricCode.CFO);                         // 6.82T LC tiền thuần từ HĐKD ✅
        CASH_FLOW_MAP.put("cfa23", MetricCode.CAPEX);                       // -1.86T Mua sắm TSCĐ ✅
        CASH_FLOW_MAP.put("cfa26", MetricCode.CFI);                         // -2.92T LC tiền thuần từ HĐĐT ✅
        CASH_FLOW_MAP.put("cfa34", MetricCode.INTEREST_PAID);               // -0.77T Lãi vay đã trả
        CASH_FLOW_MAP.put("cfa35", MetricCode.CFF);                         // 3.13T LC tiền thuần từ HĐTC ✅
        CASH_FLOW_MAP.put("cfa36", MetricCode.NET_CASH_CHANGE);             // 8.33T Tăng/giảm tiền thuần ✅
        // Note: DIVIDENDS_PAID not clearly mapped in HPG Q1 data — skip for now
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the field mapping for a given report section.
     *
     * @param section VCI section name: INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW
     * @return mapping of VCI field codes to MetricCode
     */
    public static Map<String, MetricCode> getMapping(String section) {
        return switch (section) {
            case "INCOME_STATEMENT" -> INCOME_STATEMENT_MAP;
            case "BALANCE_SHEET" -> BALANCE_SHEET_MAP;
            case "CASH_FLOW" -> CASH_FLOW_MAP;
            default -> Map.of();
        };
    }

    /**
     * Resolve a VCI field code to a MetricCode for a given report section.
     *
     * @param fieldCode VCI field code (e.g., "isa3", "bsa60")
     * @param section   report section (e.g., "INCOME_STATEMENT")
     * @return Optional containing the MetricCode, or empty if unmapped/skipped
     */
    public static Optional<MetricCode> resolve(String fieldCode, String section) {
        Map<String, MetricCode> mapping = getMapping(section);
        if (!mapping.containsKey(fieldCode)) {
            return Optional.empty(); // Unmapped field
        }
        MetricCode code = mapping.get(fieldCode);
        if (code == null) {
            return Optional.empty(); // Explicitly skipped field
        }
        return Optional.of(code);
    }
}
