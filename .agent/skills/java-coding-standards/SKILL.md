---
name: java-coding-standards
description: "Java coding standards for Spring Boot services: naming, immutability, Optional usage, streams, exceptions, generics, and project layout."
origin: ECC
---

# Java Coding Standards

Standards for readable, maintainable Java (17+) code in Spring Boot services.

## When to Use

- Writing or reviewing Java code in Spring Boot projects
- Enforcing naming, immutability, or exception handling conventions
- Working with records, sealed classes, or pattern matching (Java 17+)
- Reviewing use of Optional, streams, or generics
- Structuring packages and project layout

## Core Principles

- Prefer clarity over cleverness
- Immutable by default; minimize shared mutable state
- Fail fast with meaningful exceptions
- Consistent naming and package structure

## Naming

```java
// PASS: Classes/Records: PascalCase
public class MarketService {}
public record Money(BigDecimal amount, Currency currency) {}

// PASS: Methods/fields: camelCase
private final MarketRepository marketRepository;
public Market findBySlug(String slug) {}

// PASS: Constants: UPPER_SNAKE_CASE
private static final int MAX_PAGE_SIZE = 100;

// PASS: REST controllers named as *Controller
public class MarketController {}
```

## Immutability

```java
// PASS: Favor records and final fields for DTOs
public record MarketDto(Long id, String name, MarketStatus status) {}

public class Market {
  private final Long id;
  private final String name;
  // getters only, no setters
}
```

## Optional Usage

```java
// PASS: Return Optional from find* methods
Optional<Market> market = marketRepository.findBySlug(slug);

// PASS: Map/flatMap instead of get()
return market
    .map(MarketResponse::from)
    .orElseThrow(() -> new EntityNotFoundException("Market not found"));
```

## Streams Best Practices

```java
// PASS: Use streams for transformations, keep pipelines short
List<String> names = markets.stream()
    .map(Market::name)
    .filter(Objects::nonNull)
    .toList();

// FAIL: Avoid complex nested streams; prefer loops for clarity
```

## Dependency Injection

```java
// PASS: Constructor injection (preferred over @Autowired on fields)
@Service
@RequiredArgsConstructor
public class MarketService {
  private final MarketRepository marketRepository;
}

// FAIL: Field injection with @Autowired
@Autowired
private MarketRepository marketRepository; // use constructor injection
```

## Exceptions

- Use unchecked exceptions for domain errors; wrap technical exceptions with context
- Create domain-specific exceptions (e.g., `MarketNotFoundException`)
- Avoid broad `catch (Exception ex)` unless rethrowing/logging centrally

### Centralised Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MarketNotFoundException.class)
  public ResponseEntity<ErrorResponse> handle(MarketNotFoundException ex) {
    return ResponseEntity.status(404).body(ErrorResponse.from(ex));
  }
}
```

## Project Structure

### Maven/Gradle Multi-Module

```
lamb-feature-interface/
  dto/
  api/
lamb-feature-impl/
  controller/
  service/
  repository/
  domain/
  mapper/
```

## Formatting and Style

- One public top-level type per file
- Keep methods short and focused; extract helpers
- Order members: constants, fields, constructors, public methods, protected, private

## Code Smells to Avoid

- Long parameter lists → use DTO/builders
- Deep nesting → early returns
- Magic numbers → named constants
- Static mutable state → prefer dependency injection
- Silent catch blocks → log and act or rethrow

## Logging

```java
// SLF4J (Lombok @Slf4j is preferred)
@Slf4j
@Service
public class MarketService {
    public void fetchMarket(String slug) {
        log.info("Fetching market slug={}", slug);
    }
}
```

## Null Handling

- Accept `@Nullable` only when unavoidable; otherwise use `@NonNull`
- Use Bean Validation (`@NotNull`, `@NotBlank`) on inputs

## Configuration

```java
// @ConfigurationProperties
@ConfigurationProperties(prefix = "market")
public record MarketProperties(int maxPageSize, Duration cacheTtl) {}
```

## Testing Expectations

- JUnit 5 + AssertJ for fluent assertions
- Mockito for mocking; avoid partial mocks where possible
- `@WebMvcTest` for controller slices, `@DataJpaTest` for repository slices
- `@SpringBootTest` reserved for full integration tests
- `@MockBean` for replacing beans in Spring context

```java
// Controller test
@WebMvcTest(MarketController.class)
class MarketControllerTest {
  @Autowired MockMvc mockMvc;
  @MockBean MarketService marketService;
}
```

**Remember**: Keep code intentional, typed, and observable. Optimize for maintainability over micro-optimizations unless proven necessary.
