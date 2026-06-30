---
name: hexagonal-architecture
description: Design, implement, and refactor Ports & Adapters systems with clear domain boundaries, dependency inversion, and testable use-case orchestration for Java Spring Boot services.
origin: ECC
---

# Hexagonal Architecture (Ports and Adapters)

Hexagonal architecture (Ports and Adapters) keeps business logic independent from frameworks, transport, and persistence details. The core app depends on abstract ports, and adapters implement those ports at the edges.

## When to Use

- Building new features where long-term maintainability and testability matter.
- Refactoring layered or framework-heavy code where domain logic is mixed with I/O concerns.
- Replacing infrastructure (database, external APIs, message bus) without rewriting business rules.

## Core Concepts

- **Domain model**: Business rules and entities. No framework imports.
- **Use cases (application layer)**: Orchestrate domain behavior and workflow steps (`Service`).
- **Inbound ports**: Contracts describing what the application can do (`Service` Interfaces).
- **Outbound ports**: Contracts for dependencies the application needs (`Repository` Interfaces, `Gateway` Interfaces).
- **Adapters**: Infrastructure implementations of ports (`Controller`, `JpaRepository` impl, `FeignClient`).

Dependency direction is always inward:
- Adapters -> application/domain
- Application -> port interfaces

## Module Layout in Maven

```text
lamb-feature/
  ├── lamb-feature-interface/   # Inbound ports (API contracts, DTOs)
  │     ├── dto/
  │     └── api/
  ├── lamb-feature-impl/        # Core & Outbound adapters
  │     ├── domain/             # Entities
  │     ├── repository/         # Outbound Ports (Interfaces)
  │     ├── service/            # Use Cases (Inbound implementations)
  │     ├── controller/         # Inbound Adapters (REST)
  │     └── client/             # Outbound Adapters (Ebao, MoMo)
```

## Java Spring Boot Example

### 1. Outbound Port (Repository Interface)

```java
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String orderId);
}
```

### 2. Outbound Port (Gateway Interface)

```java
public interface PaymentGateway {
    PaymentAuth authorize(String orderId, BigDecimal amount);
}
```

### 3. Use Case (Service Implementation)

```java
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public OrderResponse execute(CreateOrderCommand command) {
        Order order = new Order(command.getOrderId(), command.getAmount());

        PaymentAuth auth = paymentGateway.authorize(order.getId(), order.getAmount());
        order.markAuthorized(auth.getAuthorizationId());

        orderRepository.save(order);

        return new OrderResponse(order.getId(), auth.getAuthorizationId());
    }
}
```

### 4. Inbound Adapter (Controller)

```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand(request.getOrderId(), request.getAmount());
        OrderResponse response = createOrderUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

### 5. Outbound Adapter (JPA Repository Implementation)

```java
@Repository
@RequiredArgsConstructor
public class PostgresOrderRepository implements OrderRepository {

    private final SpringDataJpaOrderRepository springDataRepo; // The actual Spring interface

    @Override
    public void save(Order order) {
        OrderEntity entity = OrderMapper.toEntity(order);
        springDataRepo.save(entity);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return springDataRepo.findById(orderId).map(OrderMapper::toDomain);
    }
}
```

## Anti-Patterns to Avoid

- Domain entities importing Spring Web (`@RestController`) or specific DB annotations (mixing JPA `@Entity` with strict domain logic, though in practical Spring Boot we often accept JPA annotations on domain models as a pragmatic compromise).
- Use cases reading directly from `HttpServletRequest`.
- Spreading dependency wiring across many files with hidden global singletons.

## Best Practices Checklist

- Domain and use-case layers import only internal types and ports.
- Every external dependency (DB, external API) is represented by an outbound port interface.
- Validation occurs at boundaries (Controller `@Valid`).
- Errors are translated across boundaries (infra errors -> application/domain errors using `@ControllerAdvice`).
