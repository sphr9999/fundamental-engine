package com.eyelanding.fundamentalengine.infrastructure.vci.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;

/**
 * Parses VCI financial statement API responses.
 *
 * <p>VCI response format:
 * <pre>
 * {
 *   "status": 200,
 *   "successful": true,
 *   "data": {
 *     "years": [
 *       {"ticker":"HPG", "yearReport":2025, "lengthReport":5, "isa3":52900847302653, ...}
 *     ],
 *     "quarters": [
 *       {"ticker":"HPG", "yearReport":2026, "lengthReport":1, "isa3":52900847302653, ...}
 *     ]
 *   }
 * }
 * </pre>
 *
 * <p>Each record has coded field names (isa1, bsa60, cfa20) with numeric values.
 * The {@code yearReport} and {@code lengthReport} fields identify the period:
 * <ul>
 *   <li>lengthReport=1..4 → Quarter (Q1..Q4)</li>
 *   <li>lengthReport=5 → Full year</li>
 * </ul>
 */
@Slf4j
public final class VciResponseParser {

    private VciResponseParser() {}

    /**
     * Parse VCI wrapper response into structured financial items.
     *
     * @param rawJson      raw JSON response body
     * @param periodFilter "Q" for quarters, "Y" for years
     * @param mapper       ObjectMapper for JSON parsing
     * @return list of financial items, or empty if response is invalid
     */
    public static Optional<List<VciFinancialItem>> parseVciResponse(
            String rawJson, String periodFilter, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(rawJson);

            // Check if response is successful
            if (!root.path("successful").asBoolean(false)) {
                String msg = root.path("exception").asText("Unknown error");
                log.warn("VCI API returned error: {}", msg);
                return Optional.empty();
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                log.debug("VCI response has no data node");
                return Optional.empty();
            }

            // Select years or quarters based on period filter
            String arrayKey = "Q".equalsIgnoreCase(periodFilter) ? "quarters" : "years";
            JsonNode records = data.path(arrayKey);
            if (records.isMissingNode() || !records.isArray() || records.isEmpty()) {
                log.debug("VCI response has no {} data", arrayKey);
                return Optional.empty();
            }

            List<VciFinancialItem> items = new ArrayList<>();

            // Each record is one period (e.g., HPG Q1.2026)
            // We transform it into VciFinancialItem per FIELD, with one period value each
            // But for efficiency, we group by field across all periods

            // Collect all field codes from the first record
            JsonNode firstRecord = records.get(0);
            Set<String> fieldCodes = new LinkedHashSet<>();
            firstRecord.fieldNames().forEachRemaining(name -> {
                if (isFinancialField(name)) {
                    fieldCodes.add(name);
                }
            });

            // For each field code, create a VciFinancialItem with values across all periods
            for (String fieldCode : fieldCodes) {
                Map<String, BigDecimal> periodValues = new LinkedHashMap<>();

                for (JsonNode record : records) {
                    int yearReport = record.path("yearReport").asInt();
                    int lengthReport = record.path("lengthReport").asInt();

                    String periodCode = toPeriodCode(yearReport, lengthReport);
                    if (periodCode == null) continue;

                    JsonNode valueNode = record.path(fieldCode);
                    if (!valueNode.isMissingNode() && !valueNode.isNull() && valueNode.isNumber()) {
                        periodValues.put(periodCode, valueNode.decimalValue());
                    }
                }

                if (!periodValues.isEmpty()) {
                    VciFinancialItem item = new VciFinancialItem();
                    item.setFieldName(fieldCode);
                    item.setItemId(fieldCode);
                    item.setName(fieldCode); // We don't have Vietnamese labels from API
                    item.setPeriodValues(periodValues);
                    items.add(item);
                }
            }

            log.debug("Parsed {} financial items from {} {} records",
                    items.size(), records.size(), arrayKey);
            return Optional.of(items);

        } catch (Exception e) {
            log.error("Failed to parse VCI response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if a field name is a financial data field (isa*, bsa*, cfa*, isb*, etc.)
     * Excludes metadata fields like ticker, yearReport, etc.
     */
    private static boolean isFinancialField(String name) {
        if (name == null || name.length() < 3) return false;
        // Financial fields start with: isa, isb, iss, isi, bsa, bsb, cfa, cfb
        return name.matches("^(is|bs|cf)[a-z]\\d+$");
    }

    /**
     * Convert yearReport + lengthReport to our period code format.
     *
     * @param yearReport    e.g., 2026
     * @param lengthReport  1-4 for quarters, 5 for full year
     * @return "2026Q1" for quarterly, "2025" for yearly, null if invalid
     */
    static String toPeriodCode(int yearReport, int lengthReport) {
        if (lengthReport >= 1 && lengthReport <= 4) {
            return yearReport + "Q" + lengthReport;
        } else if (lengthReport == 5) {
            return String.valueOf(yearReport);
        }
        return null;
    }

    /**
     * @deprecated Use {@link #parseVciResponse(String, String, ObjectMapper)} instead.
     * This method was for the old array-based response format.
     */
    @Deprecated
    public static List<VciFinancialItem> parseFinancialItems(List<Map<String, Object>> rawItems) {
        log.warn("parseFinancialItems(List) called — this is the old format parser, should not be used");
        return List.of();
    }
}
