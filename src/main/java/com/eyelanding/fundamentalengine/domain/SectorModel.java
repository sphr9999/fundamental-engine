package com.eyelanding.fundamentalengine.domain;

/**
 * Sector model classification.
 * Bank/Securities/Insurance require different financial analysis models.
 */
public enum SectorModel {
    GENERAL,
    BANK,
    SECURITIES,
    INSURANCE,
    REAL_ESTATE,
    UNKNOWN
}
