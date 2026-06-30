package com.eyelanding.fundamentalengine.config.redis;

/**
 * Redis configuration placeholder.
 *
 * <p>Redis has been replaced by Caffeine in-memory cache for single-instance
 * deployments. See {@link com.eyelanding.fundamentalengine.infrastructure.config.RedisCacheConfig}
 * for cache configuration.</p>
 *
 * <p>To re-enable Redis, add {@code spring-boot-starter-data-redis} to pom.xml
 * and restore LettuceConnectionFactory + RedisTemplate beans here.</p>
 */
// @Configuration — disabled, using Caffeine instead
public class RedisConfig {
    // Intentionally empty — Caffeine replaces Redis for cache
}