package com.eyelanding.fundamentalengine.infrastructure.vps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * HTTP client for VPS realtime stock price API.
 *
 * <p>Calls {@code GET https://bgapidatafeed.vps.com.vn/getliststockdata/{ticker}}
 * which returns realtime intraday price data (last matched price, high, low,
 * volume, etc.) during trading hours.</p>
 *
 * <p><strong>Note:</strong> VPS API returns prices in thousands (e.g. 23.3 = 23,300 VND).
 * This client converts them to full VND values.</p>
 */
@Slf4j
@Component
public class VpsHttpClient {

    private static final String BASE_URL = "https://bgapidatafeed.vps.com.vn/getliststockdata/";
    private static final BigDecimal PRICE_MULTIPLIER = new BigDecimal("1000");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        log.info("VPS HTTP client initialized — baseUrl={}", BASE_URL);
    }

    /**
     * Fetch realtime stock data for a ticker.
     *
     * @param ticker stock ticker (e.g. "HPG")
     * @return parsed stock data with prices in VND, or empty on failure
     */
    public Optional<VpsStockData> fetchRealtimePrice(String ticker) {
        String url = BASE_URL + ticker.toUpperCase();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.debug("VPS returned empty body for {}", ticker);
                return Optional.empty();
            }

            return parseResponse(body, ticker);

        } catch (Exception e) {
            log.warn("VPS request failed for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<VpsStockData> parseResponse(String body, String ticker) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // VPS returns an array, take first element
            JsonNode data;
            if (root.isArray() && !root.isEmpty()) {
                data = root.get(0);
            } else if (root.isObject()) {
                data = root;
            } else {
                log.debug("VPS unexpected response format for {}", ticker);
                return Optional.empty();
            }

            BigDecimal lastPrice = priceInVnd(data, "lastPrice");
            if (lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("VPS no valid lastPrice for {}", ticker);
                return Optional.empty();
            }

            return Optional.of(VpsStockData.builder()
                    .ticker(ticker.toUpperCase())
                    .lastPrice(lastPrice)
                    .referencePrice(priceInVnd(data, "r"))
                    .ceilingPrice(priceInVnd(data, "c"))
                    .floorPrice(priceInVnd(data, "f"))
                    .highPrice(priceInVnd(data, "highPrice"))
                    .lowPrice(priceInVnd(data, "lowPrice"))
                    .averagePrice(priceInVnd(data, "avePrice"))
                    .totalVolume(longOrNull(data, "lot"))
                    .changePercent(decimalOrNull(data, "changePc"))
                    .build());

        } catch (Exception e) {
            log.error("Failed to parse VPS response for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    /** Convert VPS price (in thousands) to VND. */
    private BigDecimal priceInVnd(JsonNode node, String field) {
        BigDecimal raw = decimalOrNull(node, field);
        return (raw != null) ? raw.multiply(PRICE_MULTIPLIER) : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            String text = node.get(field).asText();
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            return node.get(field).asLong();
        } catch (Exception e) {
            return null;
        }
    }
}
