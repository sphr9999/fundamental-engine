package com.eyelanding.fundamentalengine.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-memory cache configuration.
 *
 * <p>Replaces Redis cache for single-instance deployments (e.g. Render free tier).
 * Cache names and TTLs are kept consistent with the previous Redis configuration.</p>
 *
 * <p>Cache names:</p>
 * <ul>
 *   <li>{@code ticker-overview}:    5 minutes (read-heavy, updated on each import)</li>
 *   <li>{@code ticker-ratios}:      5 minutes</li>
 *   <li>{@code screener}:          10 minutes (heavier to compute)</li>
 *   <li>{@code industry-benchmark}: 15 minutes (least volatile)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String CACHE_TICKER_OVERVIEW = "ticker-overview";
    public static final String CACHE_TICKER_RATIOS = "ticker-ratios";
    public static final String CACHE_SCREENER = "screener";
    public static final String CACHE_INDUSTRY_BENCHMARK = "industry-benchmark";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_TICKER_OVERVIEW,
                CACHE_TICKER_RATIOS,
                CACHE_SCREENER,
                CACHE_INDUSTRY_BENCHMARK
        );

        // Default: 5 min TTL, max 1000 entries
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());

        return manager;
    }
}
