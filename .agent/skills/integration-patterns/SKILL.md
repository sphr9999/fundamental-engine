---
name: integration-patterns
description: Integration patterns for Spring Boot microservices, covering RestTemplate/WebClient, Retry, Circuit Breaker, Idempotency, and Webhook processing.
origin: ECC
---

# Integration Patterns (Spring Boot)

Patterns for safe, reliable, and idempotent integrations with external services (e.g., Ebao, Payment Gateways like MoMo, Bao Kim, notifications).

## When to Activate

- Calling external APIs (Payment Gateways, Core Systems, Notification Services).
- Receiving incoming webhooks from external systems.
- Designing callback/notification endpoints.
- Implementing retries or circuit breakers.

## Core Principles

1. **Treat all external systems as unreliable** — expect timeouts, 5xx errors, and rate limits.
2. **Idempotency is Critical** — especially for payments and notifications. Duplicate requests must not result in duplicate state changes.
3. **Protect the System (Fail Fast)** — use circuit breakers and timeouts to prevent cascading failures.
4. **Audit and Traceability** — preserve `traceId`, `transactionId`, and external `referenceNo` in logs and DB.

## Calling External Services (HTTP Clients)

### RestTemplate / WebClient Configuration

Never use the default `RestTemplate` without configuring connection and read timeouts.

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate externalRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(15))
            // Add interceptors for logging or adding traceId
            .interceptors(new TraceIdInterceptor())
            .build();
    }
}
```

### Retry Pattern (Spring Retry)

Retry transient errors (e.g., network timeouts, 503 Service Unavailable), but DO NOT retry 4xx errors (client errors) or non-idempotent POST requests without a safe idempotency key.

```java
@Service
public class NotificationClient {

    @Retryable(
      value = { HttpServerErrorException.class, ResourceAccessException.class }, 
      maxAttempts = 3, 
      backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendNotification(NotificationRequest request) {
        restTemplate.postForObject(url, request, Void.class);
    }

    @Recover
    public void recover(Exception e, NotificationRequest request) {
        log.error("Failed to send notification after retries for request {}", request, e);
        // Fallback logic: e.g., save to DB to retry later via a cron job
    }
}
```

## Circuit Breaker (Resilience4j)

Use Circuit Breakers to prevent overwhelming external APIs and to fail fast when they are down.

```java
@Service
public class CoreSystemClient {

    @CircuitBreaker(name = "ebaoClient", fallbackMethod = "fallbackGetPolicy")
    public PolicyDTO getPolicyDetails(String policyNumber) {
        return restTemplate.getForObject(ebaoUrl + "/policies/" + policyNumber, PolicyDTO.class);
    }

    public PolicyDTO fallbackGetPolicy(String policyNumber, Throwable t) {
        log.warn("Circuit breaker triggered or error calling Ebao for policy {}: {}", policyNumber, t.getMessage());
        throw new ExternalServiceException("Ebao service unavailable", "CORE_UNAVAILABLE");
    }
}
```

## Idempotency and Webhooks (Payment Callbacks)

### Webhook/Callback Processing

When receiving callbacks from Payment Gateways (MoMo, Bao Kim):

1. **Verify Signature/Checksum**: Ensure the request is authentic.
2. **Idempotency Check**: Check if the transaction was already processed.
3. **Small Transactions**: Keep database transactions small. Acknowledge the webhook quickly.

```java
@RestController
@RequestMapping("/api/webhooks/payment")
@Slf4j
public class PaymentWebhookController {

    @PostMapping("/momo")
    public ResponseEntity<String> handleMomoCallback(@RequestBody MomoCallbackRequest request) {
        // 1. Verify Signature
        if (!momoSignatureValidator.isValid(request)) {
            log.error("Invalid MoMo signature for txn {}", request.getTransId());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        // 2. Process Idempotently
        try {
            paymentService.processPaymentCallbackIdempotently(request);
        } catch (DuplicateTransactionException e) {
            log.info("Duplicate MoMo callback received for txn {}. Ignoring.", request.getTransId());
            // Still return 200 OK to the gateway so they stop retrying
        }

        // 3. Return fast
        return ResponseEntity.ok("Success");
    }
}
```

### Database Idempotency Implementation

Use database constraints or distributed locks (Redis) to guarantee idempotency.

```java
@Service
public class PaymentService {

    @Transactional
    public void processPaymentCallbackIdempotently(MomoCallbackRequest request) {
        // Check if already processed (could use pessimistic lock or rely on unique constraint)
        Optional<PaymentTransaction> existingTxn = transactionRepository.findByGatewayTransId(request.getTransId());
        
        if (existingTxn.isPresent() && existingTxn.get().getStatus() == PaymentStatus.SUCCESS) {
            throw new DuplicateTransactionException("Transaction already processed");
        }

        // Process state transition
        PaymentTransaction txn = existingTxn.orElseThrow(() -> new NotFoundException("Transaction not found"));
        txn.setStatus(PaymentStatus.SUCCESS);
        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(txn);
        
        // Trigger downstream events (e.g., notify user)
    }
}
```

## Integration Checklist

- [ ] HTTP Clients have explicit connection and read timeouts.
- [ ] Retries are configured ONLY for transient/server errors, never 4xx.
- [ ] Circuit breaker is in place for critical synchronous downstream dependencies.
- [ ] Webhook endpoints verify signatures before processing.
- [ ] Callbacks handle duplicate requests gracefully (Idempotency).
- [ ] PII and sensitive tokens are masked/omitted in outbound logs.
