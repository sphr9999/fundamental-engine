package com.eyelanding.fundamentalengine.infrastructure.excel;

import com.eyelanding.fundamentalengine.common.FinancialPeriodParser;
import com.eyelanding.fundamentalengine.common.TextNormalizer;
import com.eyelanding.fundamentalengine.domain.MetricCode;
import com.eyelanding.fundamentalengine.domain.PeriodType;
import com.eyelanding.fundamentalengine.domain.QualityStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses financial data sheets (Doanh thu, LNST, LNG, LNST năm).
 * Each sheet has structure: [ticker col] [name col] [exchange col] [period cols...]
 *
 * Handles two header patterns found in the workbook:
 * - Pattern A (Doanh thu, LNST): Row 1 = column index numbers, Row 2 = actual headers
 * - Pattern B (LNG, LNST nam): Row 1 = actual headers directly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialSheetParser {

    private final ExcelCellValueExtractor cellExtractor;

    /**
     * Parsed row result for one ticker × one period.
     */
    public record MetricRow(
            String ticker,
            String companyName,
            String exchange,
            String periodCode,
            PeriodType periodType,
            ExcelCellValueExtractor.CellResult value,
            String sourceSheet,
            String sourceCell
    ) {}

    /**
     * Parse a sheet and return all metric rows.
     *
     * @param sheet      the POI Sheet to parse
     * @param metricCode the MetricCode for all values in this sheet
     * @param periodType the PeriodType for values in this sheet
     * @param evaluator  formula evaluator from the workbook
     */
    public List<MetricRow> parse(Sheet sheet, MetricCode metricCode,
                                  PeriodType periodType, FormulaEvaluator evaluator) {
        List<MetricRow> results = new ArrayList<>();
        String sheetName = sheet.getSheetName();

        // Step 1: Find the header row (row with ticker/Mã column)
        int headerRowIndex = findHeaderRowIndex(sheet);
        if (headerRowIndex < 0) {
            log.warn("Sheet [{}]: could not find header row, skipping", sheetName);
            return results;
        }

        // Step 2: Parse header row to find column positions
        Row headerRow = sheet.getRow(headerRowIndex);
        int tickerCol = -1;
        int nameCol = -1;
        int exchangeCol = -1;
        Map<Integer, String> periodCols = new LinkedHashMap<>(); // colIndex -> periodCode

        for (int ci = 0; ci < headerRow.getLastCellNum(); ci++) {
            String header = cellExtractor.extractString(headerRow.getCell(ci));
            if (header == null) continue;
            String normalized = TextNormalizer.normalize(header);

            if (isTickerHeader(normalized) && tickerCol < 0) {
                tickerCol = ci;
            } else if (isNameHeader(normalized) && nameCol < 0) {
                nameCol = ci;
            } else if (isExchangeHeader(normalized) && exchangeCol < 0) {
                exchangeCol = ci;
            } else {
                // Try to parse as period header
                String parsedPeriod = FinancialPeriodParser.parseToStandard(header);
                if (parsedPeriod != null) {
                    periodCols.put(ci, parsedPeriod);
                }
            }
        }

        if (tickerCol < 0) {
            log.warn("Sheet [{}]: no ticker column found", sheetName);
            return results;
        }
        if (periodCols.isEmpty()) {
            log.warn("Sheet [{}]: no period columns found", sheetName);
            return results;
        }

        log.info("Sheet [{}]: header row={}, tickerCol={}, periods={}",
                sheetName, headerRowIndex, tickerCol, periodCols.values());

        // Step 3: Parse data rows
        int dataStartRow = headerRowIndex + 1;
        for (int ri = dataStartRow; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null) continue;

            String ticker = cellExtractor.extractString(row.getCell(tickerCol));
            if (ticker == null || ticker.isBlank()) continue;
            // Skip rows that look like group headers or totals
            if (ticker.length() > 10) continue;

            String companyName = nameCol >= 0 ? cellExtractor.extractString(row.getCell(nameCol)) : null;
            String exchange = exchangeCol >= 0 ? cellExtractor.extractString(row.getCell(exchangeCol)) : null;

            for (Map.Entry<Integer, String> periodEntry : periodCols.entrySet()) {
                int colIdx = periodEntry.getKey();
                String periodCode = periodEntry.getValue();

                Cell valueCell = row.getCell(colIdx);
                ExcelCellValueExtractor.CellResult result = cellExtractor.extractNumeric(valueCell, evaluator);
                String cellRef = new org.apache.poi.ss.util.CellReference(ri, colIdx).formatAsString();

                results.add(new MetricRow(
                        ticker.toUpperCase().trim(),
                        companyName,
                        exchange,
                        periodCode,
                        periodType,
                        result,
                        sheetName,
                        cellRef
                ));
            }
        }

        log.info("Sheet [{}]: parsed {} metric rows", sheetName, results.size());
        return results;
    }

    /**
     * Find the header row by looking for a row containing ticker column headers.
     * Searches through first 10 rows.
     */
    private int findHeaderRowIndex(Sheet sheet) {
        int maxSearch = Math.min(10, sheet.getLastRowNum() + 1);
        for (int ri = 0; ri < maxSearch; ri++) {
            Row row = sheet.getRow(ri);
            if (row == null) continue;
            for (int ci = 0; ci < Math.min(5, row.getLastCellNum()); ci++) {
                String val = cellExtractor.extractString(row.getCell(ci));
                if (val == null) continue;
                String normalized = TextNormalizer.normalize(val);
                if (isTickerHeader(normalized)) return ri;
            }
        }
        return -1;
    }

    private boolean isTickerHeader(String normalized) {
        return normalized.equals("ma ck") || normalized.equals("ma cp")
                || normalized.equals("ma") || normalized.equals("ticker")
                || normalized.equals("code") || normalized.equals("ma ck")
                || normalized.startsWith("ma ");
    }

    private boolean isNameHeader(String normalized) {
        return normalized.equals("ten") || normalized.equals("ten cong ty")
                || normalized.startsWith("ten ") || normalized.equals("company")
                || normalized.equals("name");
    }

    private boolean isExchangeHeader(String normalized) {
        return normalized.equals("san") || normalized.equals("exchange")
                || normalized.equals("san gd");
    }
}
