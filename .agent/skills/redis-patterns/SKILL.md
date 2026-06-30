---
name: redis-patterns
description: Redis data structure patterns, caching strategies, distributed locks, rate limiting, and connection management for Spring Boot production applications.
origin: ECC
---

# Redis Patterns

Quick reference for Redis best practices across common Spring Boot backend use cases.

## When to Activate

- Adding caching to an application (`@Cacheable`)
- Implementing rate limiting or throttling
- Building distributed locks or coordination (ShedLock, Redisson)
- Setting up session or token storage

## Core Patterns

### Cache-Aside (Spring Cache Abstraction)

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#productId", unless = "#result == null")
    public ProductDto getProduct(Long productId) {
        return productRepository.findById(productId)
            .map(ProductMapper::toDto)
            .orElse(null);
    }
}
```

### Cache Eviction

```java
@CacheEvict(value = "products", key = "#productId")
public void updateProduct(Long productId, ProductUpdateDto data) {
    // Update to DB
    Product product = productRepository.findById(productId).orElseThrow();
    product.update(data);
    productRepository.save(product);
}
```

### Distributed Locks (ShedLock for Scheduled Tasks)

```java
@Component
public class ScheduledTasks {

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "dailyReportTask", lockAtLeastFor = "5m", lockAtMostFor = "14m")
    public void generateDailyReport() {
        // Only one instance in the cluster will execute this
    }
}
```

### Distributed Locks (Redisson for Business Logic)

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RedissonClient redissonClient;

    public void processPayment(String orderId) {
        RLock lock = redissonClient.getLock("payment:" + orderId);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // Process payment safely
            } else {
                throw new ConcurrentOperationException("Payment is already processing");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

## Key Design

### Naming Conventions

```
# Pattern: resource:id:field
user:123:profile
order:456:status
cache:product:789

# Handled automatically by Spring Cache, but good to know
```

### TTL Strategy

Always set a TTL. Keys without TTL accumulate indefinitely and cause memory pressure.
In Spring Boot, configure RedisCacheManager with specific TTLs per cache name:

```java
@Bean
public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
    return (builder) -> builder
      .withCacheConfiguration("products",
        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)))
      .withCacheConfiguration("customerData",
        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|---|---|---|
| Keys with no TTL | Memory grows unbounded | Configure default TTL in `RedisCacheConfiguration` |
| Using `@Cacheable` on private methods | Aspect doesn't trigger | Only use on public methods |
| Calling `@Cacheable` method from same class | Bypass proxy | Call via self-injected proxy or extract to another service |
| Caching large collections | Slow serialization, memory pressure | Cache individual items or use pagination |

## Examples

**Add caching to an API endpoint:**
Use `@Cacheable` on the Service layer method with a 5-minute TTL.

**Coordinate a background job across workers:**
Use ShedLock with Redis integration to ensure a `@Scheduled` task runs only once across the Kubernetes cluster.

## Quick Reference

| Pattern | When to Use |
|---------|-------------|
| `@Cacheable` | Read-heavy, tolerate slight staleness |
| `@CacheEvict` | Invalidate cache after update |
| Redisson Lock | Prevent concurrent access to a business resource |
| ShedLock | Prevent concurrent execution of cron jobs |

## Related

- Skill: `spring-boot-patterns` — Spring Boot architecture
- Skill: `integration-patterns` — Retry, Circuit Breaker
