---
name: tdd-workflow
description: Use this skill when writing new features, fixing bugs, or refactoring code. Enforces test-driven development with 80%+ coverage including unit and integration tests in Spring Boot.
origin: ECC
---

# Test-Driven Development Workflow

This skill ensures all code development follows TDD principles with comprehensive test coverage in Java Spring Boot.

## When to Activate

- Writing new features or functionality
- Fixing bugs or issues
- Refactoring existing code
- Adding API endpoints
- Creating new components

## Core Principles

### 1. Tests BEFORE Code
ALWAYS write tests first, then implement code to make tests pass.

### 2. Coverage Requirements
- Minimum 80% coverage (unit + integration)
- All edge cases covered
- Error scenarios tested
- Boundary conditions verified

### 3. Test Types

#### Unit Tests (JUnit 5 + Mockito)
- Individual services, utilities
- Business logic in domain models
- Use `@ExtendWith(MockitoExtension.class)`

#### Integration Tests (Spring Boot Test)
- API endpoints (`@WebMvcTest`)
- Database operations (`@DataJpaTest`)
- Full application context (`@SpringBootTest`)
- Testcontainers for real PostgreSQL/Kafka instances

## TDD Workflow Steps

### Step 1: Write User Journeys
```
As a [role], I want to [action], so that [benefit]
```

### Step 2: Generate Test Cases
For each user journey, create comprehensive test cases using JUnit 5:

```java
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private MarketService marketService;

    @Test
    void returns_relevant_markets_for_query() {
        // Arrange
        // Act
        // Assert
    }

    @Test
    void throws_exception_when_not_found() {
        // Test edge case
    }
}
```

### Step 3: Run Tests (They Should Fail)
```bash
mvn test -Dtest=MarketServiceTest
# Tests should fail - we haven't implemented yet
```

### Step 4: Implement Code
Write minimal code to make tests pass:

```java
@Service
@RequiredArgsConstructor
public class MarketService {
    private final MarketRepository marketRepository;

    public List<Market> searchMarkets(String query) {
        // Minimal implementation
        return Collections.emptyList();
    }
}
```

### Step 5: Run Tests Again
```bash
mvn test -Dtest=MarketServiceTest
# Tests should now pass
```

### Step 6: Refactor
Improve code quality while keeping tests green:
- Remove duplication
- Improve naming
- Optimize performance

### Step 7: Verify Coverage
```bash
mvn clean test jacoco:report
# Open target/site/jacoco/index.html to verify 80%+ coverage
```

## Testing Patterns

### API Integration Test Pattern (MockMvc)
```java
@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketService marketService;

    @Test
    void returns_markets_successfully() throws Exception {
        when(marketService.getMarkets()).thenReturn(List.of(new MarketDto()));

        mockMvc.perform(get("/api/v1/markets")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void validates_query_parameters() throws Exception {
        mockMvc.perform(get("/api/v1/markets?limit=invalid"))
                .andExpect(status().isBadRequest());
    }
}
```

### Database Integration Test Pattern (DataJpaTest)
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MarketRepositoryTest {

    @Autowired
    private MarketRepository marketRepository;

    @Test
    void finds_active_markets() {
        // Ensure you test against a real/test DB using Testcontainers or H2
        List<Market> markets = marketRepository.findByStatus(MarketStatus.ACTIVE);
        assertThat(markets).isNotEmpty();
    }
}
```

## Common Testing Mistakes to Avoid

### FAIL: WRONG: Testing Implementation Details
```java
// Don't verify internal private method calls using PowerMock
```

### PASS: CORRECT: Test User-Visible Behavior
```java
// Test what the method returns and the state changes it causes
assertThat(response.getStatus()).isEqualTo(MarketStatus.ACTIVE);
```

### FAIL: WRONG: No Test Isolation
```java
// Tests depend on state left by previous test
@Test
void step1_creates_user() {}
@Test
void step2_updates_user() {} // depends on step1
```

### PASS: CORRECT: Independent Tests
```java
// Each test sets up its own data
@Test
void creates_user() {
  User user = createTestUser();
  // Test logic
}

@Test
void updates_user() {
  User user = createTestUser();
  // Update logic
}
```

## Continuous Testing

```bash
# Run tests quickly without building full jar
mvn test
```

## Success Metrics

- 80%+ code coverage achieved
- All tests passing (green)
- No skipped or disabled tests without a ticket
- Fast test execution
- Tests catch bugs before production

---

**Remember**: Tests are not optional. They are the safety net that enables confident refactoring, rapid development, and production reliability.
