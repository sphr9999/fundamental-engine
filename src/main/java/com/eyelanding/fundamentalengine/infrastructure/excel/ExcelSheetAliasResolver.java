package com.eyelanding.fundamentalengine.infrastructure.excel;

import com.eyelanding.fundamentalengine.common.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves Vietnamese Excel sheet names to LogicalSheet types.
 * Matching is done after normalizing (removing diacritics, lowercasing, trimming).
 */
@Component
public class ExcelSheetAliasResolver {

    private static final Map<String, LogicalSheet> ALIAS_MAP = Map.ofEntries(
            // REVENUE
            Map.entry("doanh thu", LogicalSheet.REVENUE),

            // NPAT (Lợi nhuận sau thuế)
            Map.entry("lnst", LogicalSheet.NPAT),

            // GROSS_PROFIT (Lợi nhuận gộp)
            Map.entry("lng", LogicalSheet.GROSS_PROFIT),

            // NPAT_YEARLY
            Map.entry("lnst nam", LogicalSheet.NPAT_YEARLY),
            Map.entry("lnst năm", LogicalSheet.NPAT_YEARLY),

            // EPS_DILUTED
            Map.entry("eps pha loang", LogicalSheet.EPS_DILUTED),
            Map.entry("eps pha loãng", LogicalSheet.EPS_DILUTED),
            Map.entry("eps diluted", LogicalSheet.EPS_DILUTED),

            // SHARES_OUTSTANDING
            Map.entry("slcp luu hanh", LogicalSheet.SHARES_OUTSTANDING),
            Map.entry("slcp lưu hành", LogicalSheet.SHARES_OUTSTANDING),
            Map.entry("shares outstanding", LogicalSheet.SHARES_OUTSTANDING),

            // STOCK_PRICE
            Map.entry("gia cp", LogicalSheet.STOCK_PRICE),
            Map.entry("giá cp", LogicalSheet.STOCK_PRICE),
            Map.entry("stock price", LogicalSheet.STOCK_PRICE),

            // PB
            Map.entry("p.b", LogicalSheet.PB),
            Map.entry("pb", LogicalSheet.PB),

            // COMPANY_LIST
            Map.entry("tat ca cp", LogicalSheet.COMPANY_LIST),
            Map.entry("tất cả cp", LogicalSheet.COMPANY_LIST),
            Map.entry("all stocks", LogicalSheet.COMPANY_LIST),

            // FILTER / screener
            Map.entry("loc", LogicalSheet.FILTER),
            Map.entry("lọc", LogicalSheet.FILTER)
    );

    /**
     * Resolve a raw Excel sheet name to its LogicalSheet type.
     * Normalizes diacritics and whitespace before matching.
     */
    public LogicalSheet resolve(String rawSheetName) {
        if (rawSheetName == null) return LogicalSheet.UNKNOWN;
        String normalized = TextNormalizer.normalize(rawSheetName);
        return ALIAS_MAP.getOrDefault(normalized, LogicalSheet.UNKNOWN);
    }
}
