package com.eyelanding.fundamentalengine.application.importbatch;

import com.eyelanding.fundamentalengine.api.dto.ImportPreviewResponse;
import com.eyelanding.fundamentalengine.common.TextNormalizer;
import com.eyelanding.fundamentalengine.infrastructure.excel.ExcelCellValueExtractor;
import com.eyelanding.fundamentalengine.infrastructure.excel.ExcelSheetAliasResolver;
import com.eyelanding.fundamentalengine.infrastructure.excel.LogicalSheet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Previews an Excel workbook before committing to import.
 * No data is persisted — read-only analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportPreviewService {

    private final ExcelSheetAliasResolver sheetAliasResolver;
    private final ExcelCellValueExtractor cellExtractor;

    public ImportPreviewResponse preview(InputStream inputStream, String fileName,
                                          String reportPeriod) throws Exception {
        // Compute checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes;
        try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
            fileBytes = dis.readAllBytes();
        }
        String checksum = HexFormat.of().formatHex(digest.digest());

        List<ImportPreviewResponse.SheetPreview> sheetPreviews = new ArrayList<>();
        List<String> globalWarnings = new ArrayList<>();
        boolean safe = true;

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(fileBytes))) {
            int totalSheets = workbook.getNumberOfSheets();

            // Track which logical sheets we found
            Set<LogicalSheet> foundLogical = new HashSet<>();

            for (int si = 0; si < totalSheets; si++) {
                Sheet sheet = workbook.getSheetAt(si);
                String sheetName = workbook.getSheetName(si);
                LogicalSheet logical = sheetAliasResolver.resolve(sheetName);
                boolean recognized = logical != LogicalSheet.UNKNOWN;

                List<String> sheetIssues = new ArrayList<>();
                Map<String, Integer> sampleTickers = new LinkedHashMap<>();

                if (recognized) {
                    foundLogical.add(logical);
                    // Estimate data rows and sample first 5 tickers
                    int dataRows = 0;
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        var cell = row.getCell(0);
                        if (cell == null) continue;
                        String val = cellExtractor.extractString(cell);
                        if (val == null || val.isBlank()) continue;
                        String normalized = TextNormalizer.normalize(val);
                        // Skip header-like rows
                        if (normalized != null && (normalized.startsWith("ma") || normalized.contains("ticker")))
                            continue;
                        dataRows++;
                        if (sampleTickers.size() < 5) {
                            sampleTickers.merge(val.trim().toUpperCase(), 1, Integer::sum);
                        }
                    }
                    if (dataRows == 0) {
                        sheetIssues.add("Sheet appears empty");
                        safe = false;
                    }
                    sheetPreviews.add(ImportPreviewResponse.SheetPreview.builder()
                            .sheetName(sheetName)
                            .logicalSheet(logical.name())
                            .recognized(true)
                            .estimatedRows(dataRows)
                            .sampleTickers(sampleTickers)
                            .issues(sheetIssues)
                            .build());
                } else {
                    sheetPreviews.add(ImportPreviewResponse.SheetPreview.builder()
                            .sheetName(sheetName)
                            .logicalSheet("UNKNOWN")
                            .recognized(false)
                            .estimatedRows(0)
                            .sampleTickers(Map.of())
                            .issues(List.of("Sheet not recognized — will be skipped"))
                            .build());
                }
            }

            // Check for missing critical sheets
            List<LogicalSheet> critical = List.of(
                    LogicalSheet.REVENUE, LogicalSheet.NPAT, LogicalSheet.STOCK_PRICE);
            for (LogicalSheet needed : critical) {
                if (!foundLogical.contains(needed)) {
                    globalWarnings.add("Critical sheet missing: " + needed.name());
                    safe = false;
                }
            }

            return ImportPreviewResponse.builder()
                    .fileName(fileName)
                    .checksum(checksum)
                    .reportPeriod(reportPeriod)
                    .totalSheets(totalSheets)
                    .sheets(sheetPreviews)
                    .warnings(globalWarnings)
                    .safe(safe)
                    .build();
        }
    }
}
