package com.eyelanding.fundamentalengine.infrastructure.excel;

import com.eyelanding.fundamentalengine.domain.QualityStatus;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Safely extracts values from Excel cells.
 * Handles all cell types: numeric, string, formula (with cached result), error, blank.
 * Never silently converts errors or blanks to zero.
 */
@Component
public class ExcelCellValueExtractor {

    private final DataFormatter dataFormatter = new DataFormatter();

    public record CellResult(BigDecimal numericValue, String textValue,
                              QualityStatus quality, String qualityNote, String errorCode) {

        public static CellResult ok(BigDecimal value) {
            return new CellResult(value, null, QualityStatus.OK, null, null);
        }

        public static CellResult missing() {
            return new CellResult(null, null, QualityStatus.MISSING, "Blank cell", null);
        }

        public static CellResult formulaError(String errorCode) {
            return new CellResult(null, null, QualityStatus.FORMULA_ERROR,
                    "Formula error: " + errorCode, errorCode);
        }

        public static CellResult text(String text) {
            return new CellResult(null, text, QualityStatus.MISSING, "Non-numeric text: " + text, null);
        }
    }

    /**
     * Extract numeric value from a cell.
     * Returns CellResult with appropriate QualityStatus.
     */
    public CellResult extractNumeric(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return CellResult.missing();
        }

        CellType effectiveType = cell.getCellType();

        // Evaluate formula to get actual value
        if (effectiveType == CellType.FORMULA) {
            try {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated == null) return CellResult.missing();
                effectiveType = evaluated.getCellType();

                if (effectiveType == CellType.ERROR) {
                    String errCode = FormulaError.forInt(evaluated.getErrorValue()).getString();
                    return CellResult.formulaError(errCode);
                }
                if (effectiveType == CellType.NUMERIC) {
                    return CellResult.ok(BigDecimal.valueOf(evaluated.getNumberValue()));
                }
                if (effectiveType == CellType.STRING) {
                    return tryParseString(evaluated.getStringValue());
                }
                if (effectiveType == CellType.BLANK) {
                    return CellResult.missing();
                }
            } catch (Exception e) {
                // Fallback: try reading cached value
                try {
                    return CellResult.ok(BigDecimal.valueOf(cell.getNumericCellValue()));
                } catch (Exception ex) {
                    return CellResult.formulaError("EVAL_ERROR");
                }
            }
        }

        return switch (effectiveType) {
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield CellResult.ok(BigDecimal.valueOf(d));
            }
            case STRING -> tryParseString(cell.getStringCellValue());
            case BOOLEAN -> CellResult.ok(cell.getBooleanCellValue() ? BigDecimal.ONE : BigDecimal.ZERO);
            case ERROR -> {
                String errCode = FormulaError.forInt(cell.getErrorCellValue()).getString();
                yield CellResult.formulaError(errCode);
            }
            case BLANK, _NONE -> CellResult.missing();
            default -> CellResult.missing();
        };
    }

    /**
     * Extract string value from a cell (for ticker/name/exchange columns).
     */
    public String extractString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            case NUMERIC -> dataFormatter.formatCellValue(cell).trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                // Try cached formula result first (works for pre-calculated cells)
                try {
                    org.apache.poi.ss.usermodel.CellType cached = cell.getCachedFormulaResultType();
                    if (cached == org.apache.poi.ss.usermodel.CellType.STRING) {
                        String s = cell.getRichStringCellValue().getString().trim();
                        yield s.isEmpty() ? null : s;
                    } else if (cached == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        yield dataFormatter.formatCellValue(cell).trim();
                    }
                } catch (Exception ignored) {}
                String s = dataFormatter.formatCellValue(cell).trim();
                yield s.isEmpty() ? null : s;
            }
            default -> null;
        };
    }

    /**
     * Extract string value from a cell, using FormulaEvaluator for formula cells.
     * Use this when the cell may contain VLOOKUP or other cross-sheet formulas.
     */
    public String extractStringEvaluated(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA && evaluator != null) {
            try {
                org.apache.poi.ss.usermodel.CellValue cv = evaluator.evaluate(cell);
                if (cv == null) return null;
                return switch (cv.getCellType()) {
                    case STRING -> {
                        String s = cv.getStringValue().trim();
                        yield s.isEmpty() ? null : s;
                    }
                    case NUMERIC -> dataFormatter.formatCellValue(cell).trim();
                    case BOOLEAN -> String.valueOf(cv.getBooleanValue());
                    default -> null;
                };
            } catch (Exception e) {
                // Fall through to non-evaluated extraction
            }
        }
        return extractString(cell);
    }

    /**
     * Get cell reference string (e.g., "A1", "B5") for traceability.
     */
    public String getCellRef(Cell cell) {
        if (cell == null) return null;
        return new CellReference(cell.getRowIndex(), cell.getColumnIndex()).formatAsString();
    }

    private CellResult tryParseString(String value) {
        if (value == null || value.isBlank()) return CellResult.missing();
        String cleaned = value.trim().replace(",", "").replace(" ", "");
        try {
            return CellResult.ok(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            // Check if it's a formula error string
            if (cleaned.startsWith("#")) {
                return CellResult.formulaError(cleaned);
            }
            return CellResult.text(value.trim());
        }
    }
}
