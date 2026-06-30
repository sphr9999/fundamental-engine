package com.eyelanding.fundamentalengine.infrastructure.vci;

import com.eyelanding.fundamentalengine.infrastructure.vci.dto.VciResponseParser;
import com.eyelanding.fundamentalengine.infrastructure.vci.dto.VciFinancialItem;
import com.eyelanding.fundamentalengine.infrastructure.vci.dto.VciCompanyInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HTTP client for the VCI (Vietcap IQ) financial data API.
 *
 * <p>Wraps Spring's {@link RestTemplate} with production-grade resilience:</p>
 * <ul>
 *   <li><strong>Rate limiting</strong> — minimum delay between requests via
 *       {@link VciRateLimiter}</li>
 *   <li><strong>Circuit breaker</strong> — stops requests after consecutive
 *       failures via {@link VciCircuitBreaker}</li>
 *   <li><strong>Retry with backoff</strong> — exponential backoff on 429 and
 *       5xx responses</li>
 *   <li><strong>Browser-like headers</strong> — randomized User-Agent, Referer,
 *       and Origin</li>
 * </ul>
 *
 * <p>This is an infrastructure component. It performs no business logic — it
 * only handles HTTP transport, serialization, and resilience.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VciHttpClient {

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) "
                    + "Gecko/20100101 Firefox/128.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    };

    private final VciApiConfig config;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private VciRateLimiter rateLimiter;
    private VciCircuitBreaker circuitBreaker;

    /**
     * Initializes the RestTemplate, rate limiter, and circuit breaker from
     * configuration after dependency injection.
     */
    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getReadTimeoutMs());

        this.restTemplate = new RestTemplate(factory);
        this.rateLimiter = new VciRateLimiter(config.getRequestDelayMs());
        this.circuitBreaker = new VciCircuitBreaker(
                config.getCircuitBreakerFailureThreshold(),
                config.getCircuitBreakerResetMs()
        );

        log.info("VCI HTTP client initialized — baseUrl={}, delayMs={}, maxRetries={}, "
                        + "cbThreshold={}, cbResetMs={}",
                config.getBaseUrl(),
                config.getRequestDelayMs(),
                config.getMaxRetries(),
                config.getCircuitBreakerFailureThreshold(),
                config.getCircuitBreakerResetMs());
    }

    // ── Generic fetch ───────────────────────────────────────────────────

    /**
     * Executes a GET request to the VCI API with rate limiting, circuit
     * breaker protection, and retry logic.
     *
     * @param path         relative API path (e.g. {@code "/v1/company/HPG/financial-statement"})
     * @param params       query parameters appended to the URL; may be empty
     * @param responseType the expected response class
     * @param <T>          response type
     * @return the response body wrapped in {@link Optional}, or
     *         {@link Optional#empty()} on failure (after exhausting retries)
     * @throws VciCircuitOpenException if the circuit breaker is open
     */
    public <T> Optional<T> fetch(String path, Map<String, String> params,
                                 Class<T> responseType) {
        assertCircuitAllows();

        String url = buildUrl(path, params);
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            try {
                acquireRateLimit();

                log.debug("VCI request [attempt {}/{}]: GET {}",
                        attempt, config.getMaxRetries(), url);

                ResponseEntity<T> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, responseType);

                circuitBreaker.recordSuccess();

                log.debug("VCI response: status={}, bodyPresent={}",
                        response.getStatusCode(), response.getBody() != null);

                return Optional.ofNullable(response.getBody());

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("VCI 429 Too Many Requests [attempt {}/{}]: {}",
                            attempt, config.getMaxRetries(), url);
                    circuitBreaker.recordFailure();
                    sleepBeforeRetry(attempt);
                } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.debug("VCI 404 Not Found: {}", url);
                    circuitBreaker.recordSuccess(); // 404 is not a server failure
                    return Optional.empty();
                } else {
                    log.warn("VCI client error {}: {} — {}",
                            e.getStatusCode(), url, e.getMessage());
                    circuitBreaker.recordFailure();
                    return Optional.empty();
                }
            } catch (HttpServerErrorException e) {
                log.warn("VCI server error {} [attempt {}/{}]: {} — {}",
                        e.getStatusCode(), attempt, config.getMaxRetries(),
                        url, e.getMessage());
                circuitBreaker.recordFailure();
                sleepBeforeRetry(attempt);
            } catch (ResourceAccessException e) {
                log.warn("VCI connection error [attempt {}/{}]: {} — {}",
                        attempt, config.getMaxRetries(), url, e.getMessage());
                circuitBreaker.recordFailure();
                sleepBeforeRetry(attempt);
            }
        }

        log.error("VCI request failed after {} attempts: {}", config.getMaxRetries(), url);
        return Optional.empty();
    }

    // ── Company overview (realtime market data) ────────────────────────

    /**
     * Fetches realtime company overview from VCI API.
     *
     * <p>Calls {@code GET /v1/company/{ticker}} which returns current price,
     * market cap, shares outstanding, analyst target/rating, sector, and
     * 52-week range.</p>
     *
     * @param ticker the stock ticker symbol (e.g. {@code "HPG"})
     * @return parsed company info, or {@link Optional#empty()} on failure
     */
    public Optional<VciCompanyInfo> fetchCompanyInfo(String ticker) {
        String path = "/v1/company/" + ticker;

        return fetchRawString(path, Map.of())
                .flatMap(body -> parseCompanyInfo(body, ticker));
    }

    private Optional<VciCompanyInfo> parseCompanyInfo(String body, String ticker) {
        try {
            var root = objectMapper.readTree(body);
            if (!root.has("data") || root.get("data").isNull()) {
                log.warn("VCI company response has no data for ticker {}", ticker);
                return Optional.empty();
            }

            var data = root.get("data");

            return Optional.of(VciCompanyInfo.builder()
                    .ticker(ticker)
                    .currentPrice(decimalOrNull(data, "currentPrice"))
                    .marketCap(decimalOrNull(data, "marketCap"))
                    .numberOfSharesMktCap(decimalOrNull(data, "numberOfSharesMktCap"))
                    .targetPrice(decimalOrNull(data, "targetPrice"))
                    .rating(textOrNull(data, "rating"))
                    .ratingAsOf(textOrNull(data, "ratingAsOf"))
                    .sector(textOrNull(data, "sector"))
                    .sectorVn(textOrNull(data, "sectorVn"))
                    .highestPrice1Year(decimalOrNull(data, "highestPrice1Year"))
                    .lowestPrice1Year(decimalOrNull(data, "lowestPrice1Year"))
                    .foreignerPercentage(decimalOrNull(data, "foreignerPercentage"))
                    .maximumForeignPercentage(decimalOrNull(data, "maximumForeignPercentage"))
                    .isBank(data.has("isBank") && data.get("isBank").asBoolean(false))
                    .build());
        } catch (Exception e) {
            log.error("Failed to parse VCI company info for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private static java.math.BigDecimal decimalOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            return new java.math.BigDecimal(node.get(field).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    // ── Financial statement convenience method ──────────────────────────

    /**
     * Fetches a financial statement from the VCI API and parses it into
     * structured {@link VciFinancialItem} objects.
     *
     * <p>Calls {@code GET /v1/company/{ticker}/financial-statement} with the
     * specified report type and period parameters.</p>
     *
     * @param ticker     the stock ticker symbol (e.g. {@code "HPG"})
     * @param reportType the statement type: {@code INCOME_STATEMENT},
     *                   {@code BALANCE_SHEET}, or {@code CASH_FLOW}
     * @param period     the period granularity: {@code "Q"} for quarterly,
     *                   {@code "Y"} for yearly
     * @return parsed financial items, or {@link Optional#empty()} on failure
     */
    public Optional<List<VciFinancialItem>> fetchFinancialStatement(
            String ticker, String reportType, String period) {
        String path = "/v1/company/" + ticker + "/financial-statement";
        Map<String, String> params = Map.of(
                "section", reportType,   // VCI uses 'section', not 'type'
                "period", period,
                "lang", "en"
        );

        return fetchRawString(path, params)
                .flatMap(body -> VciResponseParser.parseVciResponse(body, period, objectMapper));
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Fetches a raw JSON response as String.
     * Parsing is delegated to {@link VciResponseParser}.
     */
    private Optional<String> fetchRawString(
            String path, Map<String, String> params) {
        assertCircuitAllows();

        String url = buildUrl(path, params);
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            try {
                acquireRateLimit();

                log.debug("VCI request [attempt {}/{}]: GET {}",
                        attempt, config.getMaxRetries(), url);

                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                circuitBreaker.recordSuccess();

                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    log.debug("VCI returned empty body: {}", url);
                    return Optional.empty();
                }

                return Optional.of(body);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("VCI 429 Too Many Requests [attempt {}/{}]: {}",
                            attempt, config.getMaxRetries(), url);
                    circuitBreaker.recordFailure();
                    sleepBeforeRetry(attempt);
                } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.debug("VCI 404 Not Found: {}", url);
                    circuitBreaker.recordSuccess();
                    return Optional.empty();
                } else {
                    log.warn("VCI client error {}: {} — {}",
                            e.getStatusCode(), url, e.getMessage());
                    circuitBreaker.recordFailure();
                    return Optional.empty();
                }
            } catch (HttpServerErrorException e) {
                log.warn("VCI server error {} [attempt {}/{}]: {} — {}",
                        e.getStatusCode(), attempt, config.getMaxRetries(),
                        url, e.getMessage());
                circuitBreaker.recordFailure();
                sleepBeforeRetry(attempt);
            } catch (ResourceAccessException e) {
                log.warn("VCI connection error [attempt {}/{}]: {} — {}",
                        attempt, config.getMaxRetries(), url, e.getMessage());
                circuitBreaker.recordFailure();
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                log.error("VCI unexpected error: {} — {}", url, e.getMessage());
                circuitBreaker.recordFailure();
                return Optional.empty();
            }
        }

        log.error("VCI request failed after {} attempts: {}", config.getMaxRetries(), url);
        return Optional.empty();
    }

    private void assertCircuitAllows() {
        if (!circuitBreaker.allowRequest()) {
            throw new VciCircuitOpenException(
                    "VCI circuit breaker is OPEN — requests are blocked. "
                            + "Consecutive failures: "
                            + circuitBreaker.getConsecutiveFailures());
        }
    }

    private void acquireRateLimit() {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VciRequestException("Interrupted while waiting for rate limiter", e);
        }
    }

    private String buildUrl(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(config.getBaseUrl() + path);

        if (params != null) {
            params.forEach(builder::queryParam);
        }

        return builder.toUriString();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, randomUserAgent());

        if (config.getReferer() != null && !config.getReferer().isBlank()) {
            headers.set(HttpHeaders.REFERER, config.getReferer());
        }
        if (config.getOrigin() != null && !config.getOrigin().isBlank()) {
            headers.set(HttpHeaders.ORIGIN, config.getOrigin());
        }

        return headers;
    }

    private void sleepBeforeRetry(int attempt) {
        long delay = config.getRetryBaseDelayMs() * (1L << (attempt - 1));
        // Add jitter: ±25%
        long jitter = (long) (delay * 0.25 * (ThreadLocalRandom.current().nextDouble() - 0.5));
        long totalDelay = Math.max(0, delay + jitter);

        log.debug("VCI retry backoff: sleeping {} ms before attempt {}",
                totalDelay, attempt + 1);
        try {
            Thread.sleep(totalDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("VCI retry sleep interrupted");
        }
    }

    private static String randomUserAgent() {
        return USER_AGENTS[ThreadLocalRandom.current().nextInt(USER_AGENTS.length)];
    }
}
