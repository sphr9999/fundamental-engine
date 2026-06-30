package com.eyelanding.fundamentalengine.infrastructure.vci;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the VCI (Vietcap IQ) API integration.
 *
 * <p>Binds to the {@code fundamental-engine.vci} prefix in application properties.
 * Provides sensible defaults for rate limiting, retry, circuit breaker, and
 * HTTP connection tuning.</p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * fundamental-engine:
 *   vci:
 *     base-url: https://iq.vietcap.com.vn/api/iq-insight-service
 *     request-delay-ms: 200
 *     max-retries: 3
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "fundamental-engine.vci")
public class VciApiConfig {

    /**
     * Base URL for the VCI IQ Insight API.
     */
    private String baseUrl = "https://iq.vietcap.com.vn/api/iq-insight-service";

    /**
     * HTTP Referer header value sent with each request.
     */
    private String referer = "https://iq.vietcap.com.vn/";

    /**
     * HTTP Origin header value sent with each request.
     */
    private String origin = "https://iq.vietcap.com.vn";

    // ── Rate limiting ───────────────────────────────────────────────────

    /**
     * Minimum delay in milliseconds between consecutive API requests.
     */
    private long requestDelayMs = 200;

    // ── Retry ───────────────────────────────────────────────────────────

    /**
     * Maximum number of retry attempts for failed requests (429 / 5xx).
     */
    private int maxRetries = 3;

    /**
     * Base delay in milliseconds for exponential backoff between retries.
     */
    private long retryBaseDelayMs = 1_000;

    // ── HTTP timeouts ───────────────────────────────────────────────────

    /**
     * TCP connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 5_000;

    /**
     * Socket read timeout in milliseconds.
     */
    private int readTimeoutMs = 10_000;

    // ── Circuit breaker ─────────────────────────────────────────────────

    /**
     * Number of consecutive failures before the circuit breaker opens.
     */
    private int circuitBreakerFailureThreshold = 10;

    /**
     * Duration in milliseconds after which an open circuit breaker transitions
     * to half-open, allowing a single probe request.
     */
    private long circuitBreakerResetMs = 60_000;

    // ── Batching ────────────────────────────────────────────────────────

    /**
     * Maximum number of tickers to process in a single batch request cycle.
     */
    private int batchSize = 50;
}
