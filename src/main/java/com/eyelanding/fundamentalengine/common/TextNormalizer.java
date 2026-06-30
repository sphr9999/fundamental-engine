package com.eyelanding.fundamentalengine.common;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility for normalizing Vietnamese text, accents, and whitespace.
 * Used for sheet name matching and ticker lookup.
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private TextNormalizer() {
    }

    /**
     * Remove Vietnamese diacritical marks and normalize whitespace.
     */
    public static String removeAccents(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return DIACRITICAL_MARKS.matcher(normalized).replaceAll("");
    }

    /**
     * Normalize text for comparison: lowercase, remove accents, trim, collapse whitespace.
     */
    public static String normalizeForComparison(String input) {
        if (input == null) return null;
        return removeAccents(input.trim())
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    /**
     * Alias for normalizeForComparison — shorthand used throughout the codebase.
     */
    public static String normalize(String input) {
        return normalizeForComparison(input);
    }
}
