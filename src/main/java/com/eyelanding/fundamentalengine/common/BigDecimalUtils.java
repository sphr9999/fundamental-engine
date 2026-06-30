package com.eyelanding.fundamentalengine.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for safe BigDecimal operations in financial calculations.
 */
public final class BigDecimalUtils {

    public static final int DEFAULT_SCALE = 6;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private BigDecimalUtils() {
    }

    /**
     * Safe divide with null checks and zero-divisor protection.
     */
    public static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate YoY growth: (current - previous) / |previous| * 100
     */
    public static BigDecimal yoyGrowth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), DEFAULT_SCALE, DEFAULT_ROUNDING)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate margin: numerator / denominator * 100
     */
    public static BigDecimal marginPercent(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal result = safeDivide(numerator, denominator);
        return result != null ? result.multiply(BigDecimal.valueOf(100)) : null;
    }
}
