package com.eyelanding.fundamentalengine.infrastructure.excel;

import com.eyelanding.fundamentalengine.common.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the "Lọc" (Filter) sheet which contains:
 *   Col A: Mã (ticker)
 *   Col B: Tên (company name)
 *   Col C: Ngành (industry / sector)
 *
 * This is the primary source for industry classification in dim_company.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilterSheetParser {

    private final ExcelCellValueExtractor cellExtractor;

    public record CompanyInfo(
            String ticker,
            String companyName,
            String industry
    ) {}

    public List<CompanyInfo> parse(Sheet sheet, FormulaEvaluator evaluator) {
        List<CompanyInfo> result = new ArrayList<>();

        // Debug: log first 3 rows to understand actual sheet structure
        for (int r = 0; r <= Math.min(3, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) { log.debug("Row {} is null", r); continue; }
            StringBuilder sb = new StringBuilder("Row ").append(r).append(": ");
            for (int c = 0; c <= Math.min(5, row.getLastCellNum()); c++) {
                var cell = row.getCell(c);
                sb.append("[").append(c).append("]=")
                  .append(cell == null ? "null" : cellExtractor.extractString(cell)).append(" ");
            }
            log.info("FilterSheetParser DEBUG {}", sb);
        }

        int headerRow = findHeaderRow(sheet);
        if (headerRow < 0) {
            // Fallback: no header found — assume row 0 is header, use default col positions
            log.warn("FilterSheetParser: no header found in '{}', falling back to row 0", sheet.getSheetName());
            headerRow = 0;
        }

        // Detect column positions from header row
        int tickerCol = -1, nameCol = -1, industryCol = -1;
        Row header = sheet.getRow(headerRow);
        if (header == null) return result;

        for (int c = 0; c <= Math.min(10, header.getLastCellNum()); c++) {
            var cell = header.getCell(c);
            if (cell == null) continue;
            String raw = cellExtractor.extractString(cell);
            if (raw == null) continue;
            String h = TextNormalizer.normalize(raw);
            if (h == null) continue;
            if ((h.contains("ma") || h.contains("m\u00e3") || h.contains("ticker") || h.contains("code"))
                    && tickerCol < 0) {
                tickerCol = c;
            } else if ((h.contains("ten") || h.contains("t\u00ean") || h.contains("name") || h.contains("cong ty"))
                    && nameCol < 0) {
                nameCol = c;
            } else if ((h.contains("nganh") || h.contains("ng\u00e0nh") || h.contains("industry") || h.contains("sector"))
                    && industryCol < 0) {
                industryCol = c;
            }
        }

        // Fallback: columns 0=ticker, 1=name, 2=industry (based on observed sheet structure)
        if (tickerCol < 0) tickerCol = 0;
        if (nameCol < 0) nameCol = 1;
        if (industryCol < 0) industryCol = 2;

        log.info("FilterSheetParser: header at row={}, tickerCol={}, nameCol={}, industryCol={}",
                headerRow, tickerCol, nameCol, industryCol);

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            var tickerCell = row.getCell(tickerCol);
            if (tickerCell == null) continue;

            String ticker = safeStr(cellExtractor.extractString(tickerCell));
            if (ticker.isBlank() || ticker.length() > 10) continue;
            if (ticker.matches("\\d+.*")) continue; // skip numeric rows

            String name = nameCol >= 0 && row.getCell(nameCol) != null
                    ? emptyToNull(safeStr(cellExtractor.extractString(row.getCell(nameCol)))) : null;
            // Industry column uses VLOOKUP formulas — must evaluate to get real value
            String industry = industryCol >= 0 && row.getCell(industryCol) != null
                    ? emptyToNull(safeStr(cellExtractor.extractStringEvaluated(row.getCell(industryCol), evaluator))) : null;

            result.add(new CompanyInfo(ticker.toUpperCase(), name, industry));
            log.debug("FilterSheetParser: ticker={}, name={}, industry={}", ticker, name, industry);
        }

        log.info("FilterSheetParser: parsed {} companies from sheet '{}'", result.size(), sheet.getSheetName());
        return result;
    }

    private int findHeaderRow(Sheet sheet) {
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c <= Math.min(5, row.getLastCellNum()); c++) {
                var cell = row.getCell(c);
                if (cell == null) continue;
                String raw = cellExtractor.extractString(cell);
                if (raw == null) continue;
                String text = TextNormalizer.normalize(raw);
                if (text == null) continue;
                // Match Vietnamese "Mã", "Mã CP", "Ma", common column headers
                if (text.equals("ma") || text.equals("ma cp") || text.startsWith("ma ")
                        || text.contains("ticker") || text.contains("code")
                        || text.contains("ky hieu") || text.contains("symbol")) {
                    return r;
                }
            }
        }
        return -1;
    }

    /** Returns trimmed string, or "" if null. Never returns null. */
    private String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    /** Returns null if string is blank, otherwise returns the string. */
    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
