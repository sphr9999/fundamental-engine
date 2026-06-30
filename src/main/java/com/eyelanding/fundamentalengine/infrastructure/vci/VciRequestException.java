package com.eyelanding.fundamentalengine.infrastructure.vci;

/**
 * Generic runtime exception for VCI API request failures that are not
 * covered by more specific exception types.
 *
 * <p>Typical causes include thread interruption during rate limiter waits
 * and unexpected transport-level errors.</p>
 */
public class VciRequestException extends RuntimeException {

    public VciRequestException(String message) {
        super(message);
    }

    public VciRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
