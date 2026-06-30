package com.eyelanding.fundamentalengine.infrastructure.vci.mapping;

import com.eyelanding.fundamentalengine.domain.PeriodType;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes period strings from the VCI API format to the internal format
 * used by {@code fa_financial_metric.period_code}.
 *
 * <h3>Conversion rules</h3>
 * <table>
 *   <caption>VCI → Internal period mapping</caption>
 *   <tr><th>VCI format</th><th>Internal format</th><th>PeriodType</th></tr>
 *   <tr><td>{@code 2026-Q1}</td><td>{@code 2026Q1}</td><td>QUARTER</td></tr>
 *   <tr><td>{@code 2025-Q4}</td><td>{@code 2025Q4}</td><td>QUARTER</td></tr>
 *   <tr><td>{@code 2025-Y}</td><td>{@code 2025}</td><td>YEAR</td></tr>
 *   <tr><td>{@code 2025}</td><td>{@code 2025}</td><td>YEAR</td></tr>
 * </table>
 */
public final class VciPeriodNormalizer {

    /** Matches quarterly periods: {@code 2026-Q1} through {@code 2026-Q4}. */
    private static final Pattern QUARTERLY_PATTERN =
            Pattern.compile("^(\\d{4})-Q([1-4])$");

    /** Matches explicit yearly periods: {@code 2025-Y}. */
    private static final Pattern YEARLY_EXPLICIT_PATTERN =
            Pattern.compile("^(\\d{4})-Y$");

    /** Matches implicit yearly periods: plain {@code 2025}. */
    private static final Pattern YEARLY_IMPLICIT_PATTERN =
            Pattern.compile("^\\d{4}$");

    private VciPeriodNormalizer() {
        // utility class
    }

    /**
     * Normalizes a VCI period string to the internal period code format.
     *
     * @param vciPeriod the period string from the VCI API (e.g. {@code "2026-Q1"},
     *                  {@code "2025-Y"}, {@code "2025"})
     * @return the normalized period code (e.g. {@code "2026Q1"}, {@code "2025"})
     * @throws IllegalArgumentException if the period format is not recognized
     * @throws NullPointerException     if {@code vciPeriod} is null
     */
    public static String normalize(String vciPeriod) {
        Objects.requireNonNull(vciPeriod, "vciPeriod must not be null");
        String trimmed = vciPeriod.trim();

        // Quarterly: "2026-Q1" → "2026Q1"
        Matcher quarterlyMatcher = QUARTERLY_PATTERN.matcher(trimmed);
        if (quarterlyMatcher.matches()) {
            return quarterlyMatcher.group(1) + "Q" + quarterlyMatcher.group(2);
        }

        // Explicit yearly: "2025-Y" → "2025"
        Matcher yearlyExplicitMatcher = YEARLY_EXPLICIT_PATTERN.matcher(trimmed);
        if (yearlyExplicitMatcher.matches()) {
            return yearlyExplicitMatcher.group(1);
        }

        // Implicit yearly: "2025" → "2025"
        if (YEARLY_IMPLICIT_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        throw new IllegalArgumentException(
                "Unrecognized VCI period format: '" + vciPeriod + "'");
    }

    /**
     * Determines the {@link PeriodType} for a VCI period string.
     *
     * @param vciPeriod the period string from the VCI API
     * @return {@link PeriodType#QUARTER} if the period contains {@code -Q},
     *         otherwise {@link PeriodType#YEAR}
     * @throws NullPointerException if {@code vciPeriod} is null
     */
    public static PeriodType toPeriodType(String vciPeriod) {
        Objects.requireNonNull(vciPeriod, "vciPeriod must not be null");
        return vciPeriod.contains("-Q") ? PeriodType.QUARTER : PeriodType.YEAR;
    }
}
