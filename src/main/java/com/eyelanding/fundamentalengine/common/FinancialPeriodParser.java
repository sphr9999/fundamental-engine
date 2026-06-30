package com.eyelanding.fundamentalengine.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses financial period strings from Excel headers.
 * Handles formats like: Q1.2026, Q1.26, 2026Q1, 2025, etc.
 */
public final class FinancialPeriodParser {

    // Patterns: Q1.2026, Q1.26, Q1/2026
    private static final Pattern QUARTER_DOT_YEAR = Pattern.compile("Q(\\d)[\\./-](\\d{2,4})", Pattern.CASE_INSENSITIVE);
    // Patterns: 2026Q1, 2026.Q1
    private static final Pattern YEAR_QUARTER = Pattern.compile("(\\d{4})\\.?Q(\\d)", Pattern.CASE_INSENSITIVE);
    // Pattern: just year like 2025
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");

    private FinancialPeriodParser() {
    }

    /**
     * Normalize period header to standard format.
     * Quarterly: "2026Q1"
     * Yearly: "2025"
     *
     * @return normalized period code or null if not recognized
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        // Try Q1.2026 format
        Matcher m = QUARTER_DOT_YEAR.matcher(trimmed);
        if (m.find()) {
            int quarter = Integer.parseInt(m.group(1));
            String yearStr = m.group(2);
            int year = normalizeYear(yearStr);
            return year + "Q" + quarter;
        }

        // Try 2026Q1 format
        m = YEAR_QUARTER.matcher(trimmed);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int quarter = Integer.parseInt(m.group(2));
            return year + "Q" + quarter;
        }

        // Try year only
        m = YEAR_ONLY.matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Alias for normalize — used by parsers to detect period column headers.
     */
    public static String parseToStandard(String input) {
        return normalize(input);
    }

    private static int normalizeYear(String yearStr) {
        int year = Integer.parseInt(yearStr);
        if (year < 100) {
            year += 2000; // 26 → 2026
        }
        return year;
    }
}
