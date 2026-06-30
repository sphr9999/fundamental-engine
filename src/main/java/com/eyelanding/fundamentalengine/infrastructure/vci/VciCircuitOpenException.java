package com.eyelanding.fundamentalengine.infrastructure.vci;

/**
 * Thrown when a VCI API request is rejected because the circuit breaker is
 * in the {@link VciCircuitBreaker.State#OPEN OPEN} state.
 *
 * <p>Callers should catch this exception to distinguish "circuit is open"
 * from normal empty-result scenarios returned as {@link java.util.Optional#empty()}.</p>
 */
public class VciCircuitOpenException extends RuntimeException {

    public VciCircuitOpenException(String message) {
        super(message);
    }

    public VciCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
