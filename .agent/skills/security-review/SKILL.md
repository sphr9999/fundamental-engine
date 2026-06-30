---
name: security-review
description: Use this skill when adding authentication, handling user input, working with secrets, creating API endpoints, or implementing payment/sensitive features in Spring Boot. Provides comprehensive security checklist and patterns.
origin: ECC
---

# Security Review Skill

This skill ensures all Spring Boot code follows security best practices and identifies potential vulnerabilities, specifically tailored for the Enterprise Architecture.

## When to Activate

- Implementing authentication or authorization (Spring Security)
- Handling user input or file uploads
- Creating new API endpoints
- Working with secrets or credentials
- Implementing payment features (MoMo, Bảo Kim)
- Storing or transmitting sensitive data (PII)

## Security Checklist

### 1. Secrets Management

#### FAIL: NEVER Do This
```java
// Hardcoded secret
private static final String API_KEY = "sk-proj-xxxxx";
```

#### PASS: ALWAYS Do This
```java
@Value("${integration.momo.api-key}")
private String apiKey;

// Or better, inject via Constructor/ConfigurationProperties
```

#### Verification Steps
- [ ] No hardcoded API keys, tokens, or passwords
- [ ] All secrets injected via environment variables (`application.yml` placeholders)
- [ ] No secrets logged to console/files

### 2. Input Validation

#### Always Validate User Input
```java
import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @Email String email,
    @NotBlank @Size(max = 100) String name,
    @Min(0) @Max(150) int age
) {}

@PostMapping
public ResponseEntity<Void> createUser(@Valid @RequestBody CreateUserRequest request) {
    // request is guaranteed to be valid here
}
```

#### Verification Steps
- [ ] All user inputs validated with Jakarta Validation (`@Valid`, `@NotNull`)
- [ ] Whitelist validation (not blacklist)
- [ ] Error messages don't leak sensitive internal data

### 3. SQL Injection Prevention

#### FAIL: NEVER Concatenate SQL
```java
// DANGEROUS - SQL Injection vulnerability
String query = "SELECT * FROM users WHERE email = '" + userEmail + "'";
entityManager.createNativeQuery(query);
```

#### PASS: ALWAYS Use Parameterized Queries (Spring Data JPA)
```java
// Safe - parameterized query
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailCustom(@Param("email") String email);
}
```

#### Verification Steps
- [ ] All database queries use parameterized queries (JPA/Hibernate handles this)
- [ ] No string concatenation in `@Query` or `JdbcTemplate`

### 4. Authentication & Authorization

#### Authorization Checks (Spring Security)
```java
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public void deleteUser(@PathVariable Long id) {
    // Only admins can reach here
}

@PreAuthorize("#userId == authentication.principal.id")
@GetMapping("/{userId}/profile")
public Profile getProfile(@PathVariable Long userId) {
    // User can only view their own profile
}
```

#### Verification Steps
- [ ] `@PreAuthorize` used to restrict endpoint access
- [ ] Service layer validates ownership if needed
- [ ] Token validation interceptors/filters are securely implemented

### 5. Sensitive Data Exposure (PII)

#### Logging

**PROJECT RULE: NEVER log/expose raw PII, payment data, tokens, Firebase IDs, identity docs, or secrets.**

```java
// FAIL: WRONG: Logging sensitive data
log.info("User login attempt for {}", password);
log.info("Payment details: cardNumber={}, cvv={}", cardNumber, cvv);

// PASS: CORRECT: Redact sensitive data
log.info("User login attempt for userId={}", userId);
log.info("Payment details: last4={}, userId={}", maskCard(cardNumber), userId);
```

#### Error Messages
```java
// FAIL: WRONG: Exposing internal details
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleError(Exception e) {
    return ResponseEntity.status(500).body(e.getMessage()); // Leaks DB errors
}

// PASS: CORRECT: Generic error messages
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleError(Exception e) {
    log.error("Internal error occurred", e);
    return ResponseEntity.status(500).body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred."));
}
```

#### Verification Steps
- [ ] No passwords, tokens, or PII in logs
- [ ] Error messages generic for users
- [ ] Detailed errors only in server logs
- [ ] No stack traces exposed to clients via REST API

### 6. Idempotency (Payments & Webhooks)

When integrating with external providers (MoMo, Ebao), endpoints must be idempotent.

```java
@Transactional
public void processPaymentWebhook(PaymentWebhookPayload payload) {
    // 1. Check if transaction already processed
    if (transactionRepository.existsByReferenceIdAndStatus(payload.getRefId(), SUCCESS)) {
        log.info("Transaction {} already processed, skipping", payload.getRefId());
        return;
    }
    // 2. Process logic
}
```

## Pre-Deployment Security Checklist

Before ANY production deployment:

- [ ] **Secrets**: No hardcoded secrets, all injected.
- [ ] **Input Validation**: All requests validated with `@Valid`.
- [ ] **SQL Injection**: Using Spring Data JPA / `@Param`.
- [ ] **Authorization**: `@PreAuthorize` constraints applied.
- [ ] **Idempotency**: Payment and webhook endpoints handle duplicate requests safely.
- [ ] **Error Handling**: `@ControllerAdvice` returns sanitized errors.
- [ ] **Logging**: PII and secrets are strictly excluded from SLF4J logs.
- [ ] **Dependencies**: Maven dependencies scanned (OWASP).
