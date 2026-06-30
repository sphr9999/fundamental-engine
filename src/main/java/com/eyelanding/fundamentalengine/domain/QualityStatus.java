package com.eyelanding.fundamentalengine.domain;

/**
 * Data quality status for imported metrics.
 * Never silently convert invalid data to 0.
 */
public enum QualityStatus {
    OK,
    MISSING,
    NOT_REPORTED,
    NOT_APPLICABLE,
    FORMULA_ERROR,
    SUSPICIOUS,
    ESTIMATED
}
