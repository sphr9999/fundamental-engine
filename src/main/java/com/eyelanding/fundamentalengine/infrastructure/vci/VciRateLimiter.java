package com.eyelanding.fundamentalengine.infrastructure.vci;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple token-based rate limiter that enforces a minimum interval between
 * consecutive API calls.
 *
 * <p>Thread-safe: uses {@code synchronized} on the single {@link #acquire()}
 * entry point. When the minimum interval has not elapsed since the last call,
 * the calling thread sleeps for the remaining time.</p>
 *
 * <p>This is intentionally simple — no sliding window, no token bucket.
 * For a single-service integration with ~200 ms spacing this is sufficient
 * and avoids pulling in an external library.</p>
 */
@Slf4j
public class VciRateLimiter {

    private final long minIntervalMs;
    private long lastAcquireTimeMs;

    /**
     * Creates a rate limiter with the specified minimum interval.
     *
     * @param minIntervalMs minimum delay in milliseconds between consecutive
     *                      calls to {@link #acquire()}. Must be &ge; 0.
     */
    public VciRateLimiter(long minIntervalMs) {
        if (minIntervalMs < 0) {
            throw new IllegalArgumentException("minIntervalMs must be >= 0, got " + minIntervalMs);
        }
        this.minIntervalMs = minIntervalMs;
        this.lastAcquireTimeMs = 0L;
    }

    /**
     * Acquires permission to make the next API call.
     *
     * <p>If the minimum interval has not yet elapsed since the previous call,
     * this method blocks the calling thread until the interval is satisfied.</p>
     *
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting
     */
    public synchronized void acquire() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastAcquireTimeMs;

        if (elapsed < minIntervalMs) {
            long sleepMs = minIntervalMs - elapsed;
            log.trace("VCI rate limiter: sleeping {} ms to satisfy {} ms interval",
                    sleepMs, minIntervalMs);
            Thread.sleep(sleepMs);
        }

        lastAcquireTimeMs = System.currentTimeMillis();
    }
}
