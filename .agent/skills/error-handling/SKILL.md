---
name: error-handling
description: Patterns for robust error handling in Java and Spring Boot. Covers typed exceptions, @ControllerAdvice, error responses, and user-facing messages.
origin: ECC
---

# Error Handling Patterns (Java & Spring Boot)

Consistent, robust error handling patterns for Spring Boot production applications.

## When to Activate

- Designing error types or exception hierarchies for a new module or service
- Adding retry logic or circuit breakers for unreliable external dependencies
- Reviewing API endpoints for missing error handling
- Implementing user-facing error messages and feedback
- Debugging cascading failures or silent error swallowing

## Core Principles

1. **Fail fast and loudly** — surface errors at the boundary where they occur; don't bury them.
2. **Use Custom Business Exceptions** — avoid throwing generic `RuntimeException`. Create a custom hierarchy.
3. **Global Exception Handling** — use `@ControllerAdvice` / `@RestControllerAdvice` to map exceptions to standardized API responses.
4. **Never swallow errors silently** — every `catch` block must either handle, re-throw, or log (`log.error`).
5. **Errors are part of your API contract** — document every error code a client may receive.

## Java / Spring Boot Patterns

### Custom Exception Hierarchy

Define a base exception for your domain and extend it.

```java
public abstract class AppBaseException extends RuntimeException {
    private final String code;
    private final int statusCode;

    public AppBaseException(String message, String code, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String getCode() { return code; }
    public int getStatusCode() { return statusCode; }
}

public class NotFoundException extends AppBaseException {
    public NotFoundException(String resource, String id) {
        super(String.format("%s not found: %s", resource, id), "NOT_FOUND", 404);
    }
}

public class ValidationException extends AppBaseException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", 422);
    }
}

public class UnauthorizedException extends AppBaseException {
    public UnauthorizedException(String reason) {
        super(reason, "UNAUTHORIZED", 401);
    }
}
```

### Global Exception Handler (@RestControllerAdvice)

Map exceptions to standard JSON responses for API consumers.

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppBaseException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppBaseException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getCode(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.valueOf(ex.getStatusCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        // Log the full stack trace server-side, but return a generic message to the client
        log.error("Unhandled exception occurred", ex);
        ErrorResponse errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handle standard Spring validation errors (@Valid)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
                
        ErrorResponse errorResponse = new ErrorResponse("VALIDATION_ERROR", "Request validation failed", details);
        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

### Optional over Null Returns

For operations where failure (e.g., entity not found) is expected, use `Optional` rather than throwing exceptions or returning `null`.

```java
// Repository
Optional<User> findByEmail(String email);

// Service
public User getUser(String email) {
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new NotFoundException("User", email));
}
```

### Try-Catch Best Practices

```java
// BAD: Swallowing exception
try {
    processPayment();
} catch (Exception e) {
    // nothing
}

// GOOD: Log and rethrow/wrap
try {
    processPayment();
} catch (PaymentGatewayException e) {
    log.error("Payment failed for user {}", userId, e);
    throw new BusinessException("Payment processing failed", "PAYMENT_ERROR", e);
}
```

## Error Handling Checklist

Before merging any code that touches error handling:

- [ ] Every `catch` block handles, re-throws, or logs — no silent swallowing.
- [ ] API errors follow the standard envelope (e.g. `{ "code": "...", "message": "..." }`).
- [ ] User-facing messages contain no stack traces or internal technical details.
- [ ] Full error context is logged server-side via `Slf4j`.
- [ ] Custom error classes extend a base `RuntimeException` with a `code` field.
