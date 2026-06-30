package com.eyelanding.fundamentalengine.infrastructure.vci;

import lombok.extern.slf4j.Slf4j;

/**
 * Minimal circuit breaker for the VCI API integration.
 *
 * <h3>State machine</h3>
 * <pre>
 *   CLOSED ──(consecutive failures ≥ threshold)──▸ OPEN
 *   OPEN   ──(resetMs elapsed)──▸ HALF_OPEN
 *   HALF_OPEN ──(success)──▸ CLOSED
 *   HALF_OPEN ──(failure)──▸ OPEN
 * </pre>
 *
 * <p>Thread-safe: every public method is {@code synchronized} on {@code this}.</p>
 */
@Slf4j
public class VciCircuitBreaker {

    /**
     * Possible states for the circuit breaker.
     */
    public enum State {
        /** Normal operation — requests pass through. */
        CLOSED,
        /** Requests are blocked — too many consecutive failures. */
        OPEN,
        /** One probe request is allowed to test recovery. */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long resetMs;

    private State state;
    private int consecutiveFailures;
    private long lastFailureTimeMs;

    /**
     * Creates a circuit breaker.
     *
     * @param failureThreshold number of consecutive failures before the
     *                         circuit opens. Must be &ge; 1.
     * @param resetMs          duration in milliseconds after which an open
     *                         circuit transitions to half-open. Must be &ge; 0.
     */
    public VciCircuitBreaker(int failureThreshold, long resetMs) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "failureThreshold must be >= 1, got " + failureThreshold);
        }
        if (resetMs < 0) {
            throw new IllegalArgumentException("resetMs must be >= 0, got " + resetMs);
        }
        this.failureThreshold = failureThreshold;
        this.resetMs = resetMs;
        this.state = State.CLOSED;
        this.consecutiveFailures = 0;
        this.lastFailureTimeMs = 0L;
    }

    /**
     * Returns {@code true} if the circuit currently allows a request.
     *
     * <p>Side-effect: if the circuit is OPEN and the reset timeout has elapsed,
     * it transitions to HALF_OPEN and returns {@code true}.</p>
     *
     * @return {@code true} if the request may proceed
     */
    public synchronized boolean allowRequest() {
        return switch (state) {
            case CLOSED -> true;
            case HALF_OPEN -> true;
            case OPEN -> {
                long elapsed = System.currentTimeMillis() - lastFailureTimeMs;
                if (elapsed >= resetMs) {
                    log.info("VCI circuit breaker: OPEN → HALF_OPEN after {} ms", elapsed);
                    state = State.HALF_OPEN;
                    yield true;
                }
                yield false;
            }
        };
    }

    /**
     * Records a successful response, resetting the failure counter and closing
     * the circuit.
     */
    public synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            log.info("VCI circuit breaker: {} → CLOSED after successful request", state);
        }
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    /**
     * Records a failed response. If the consecutive failure count reaches the
     * threshold, the circuit opens.
     */
    public synchronized void recordFailure() {
        consecutiveFailures++;
        lastFailureTimeMs = System.currentTimeMillis();

        if (state == State.HALF_OPEN) {
            log.warn("VCI circuit breaker: HALF_OPEN → OPEN after probe failure "
                    + "(consecutive failures: {})", consecutiveFailures);
            state = State.OPEN;
        } else if (consecutiveFailures >= failureThreshold) {
            if (state != State.OPEN) {
                log.warn("VCI circuit breaker: CLOSED → OPEN after {} consecutive failures",
                        consecutiveFailures);
            }
            state = State.OPEN;
        } else {
            log.debug("VCI circuit breaker: failure {} / {} (state={})",
                    consecutiveFailures, failureThreshold, state);
        }
    }

    /**
     * Returns the current circuit breaker state.
     *
     * @return the current {@link State}
     */
    public synchronized State getState() {
        return state;
    }

    /**
     * Returns the current count of consecutive failures.
     *
     * @return consecutive failure count
     */
    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
