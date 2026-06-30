package com.eyelanding.fundamentalengine.infrastructure.excel;

import com.eyelanding.fundamentalengine.common.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses point-in-time sheets with metadata header pattern:
 * Row 1: Mã | Tên công ty | Sàn | [MetricName]
 * Row 2-4: metadata (Chỉ số TTM, Ngày, Đơn vị)
 * Row 5+: data
 *
 * Used for: EPS pha loãng, SLCP lưu hành, GIÁ CP, P.B
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointInTimeSheetParser {

    private final ExcelCellValueExtractor cellExtractor;

    public record CompanyPointRow(
            String ticker,
            String companyName,
            String exchange,
            ExcelCellValueExtractor.CellResult value,
            String sourceSheet,
            String sourceCell
    ) {}

    /**
     * Parse a point-in-time sheet. Only reads columns 0-3 (Mã, Tên, Sàn, Value).
     */
    public List<CompanyPointRow> parse(Sheet sheet,
                                        org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        List<CompanyPointRow> results = new ArrayList<>();
        String sheetName = sheet.getSheetName();

        // Find data start row: skip metadata rows, find first row with a valid ticker
        int dataStartRow = findDataStartRow(sheet);
        if (dataStartRow < 0) {
            log.warn("Sheet [{}]: cannot determine data start row", sheetName);
            return results;
        }

        log.info("Sheet [{}]: data starts at row {}", sheetName, dataStartRow);

        for (int ri = dataStartRow; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null) continue;

            String ticker = cellExtractor.extractString(row.getCell(0));
            if (ticker == null || ticker.isBlank() || ticker.length() > 10) continue;
            // skip rows that look like headers or notes
            String normalized = TextNormalizer.normalize(ticker);
            if (normalized.startsWith("ma") || normalized.startsWith("chu")) continue;

            String companyName = cellExtractor.extractString(row.getCell(1));
            String exchange = cellExtractor.extractString(row.getCell(2));

            // Value is always in column 3
            var cellResult = cellExtractor.extractNumeric(row.getCell(3), evaluator);
            String cellRef = new org.apache.poi.ss.util.CellReference(ri, 3).formatAsString();

            results.add(new CompanyPointRow(
                    ticker.toUpperCase().trim(),
                    companyName,
                    exchange,
                    cellResult,
                    sheetName,
                    cellRef
            ));
        }

        log.info("Sheet [{}]: parsed {} point-in-time rows", sheetName, results.size());
        return results;
    }

    /**
     * Find where actual data starts by skipping metadata rows.
     * Data rows have ticker in column 0 (short string, max 10 chars).
     */
    private int findDataStartRow(Sheet sheet) {
        // Look in rows 1-8 for first data row
        for (int ri = 1; ri <= Math.min(8, sheet.getLastRowNum()); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null) continue;
            String col0 = cellExtractor.extractString(row.getCell(0));
            if (col0 != null && !col0.isBlank() && col0.trim().length() <= 10) {
                // Check it's not a known metadata keyword
                String norm = TextNormalizer.normalize(col0.trim());
                if (!isMetadataRow(norm)) {
                    return ri;
                }
            }
        }
        return -1;
    }

    private boolean isMetadataRow(String normalized) {
        return normalized.startsWith("chi so") || normalized.startsWith("ngay")
                || normalized.startsWith("don vi") || normalized.startsWith("chu thich")
                || normalized.startsWith("note") || normalized.startsWith("ma ");
    }
}
