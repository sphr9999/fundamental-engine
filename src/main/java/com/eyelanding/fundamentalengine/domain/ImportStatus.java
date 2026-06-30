package com.eyelanding.fundamentalengine.domain;

/**
 * Import batch status tracking.
 */
public enum ImportStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED
}
