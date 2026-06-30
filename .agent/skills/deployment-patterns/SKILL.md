---
name: deployment-patterns
description: Deployment workflows, CI/CD pipeline patterns, Docker containerization, health checks, rollback strategies, and production readiness checklists for Java Spring Boot applications.
origin: ECC
---

# Deployment Patterns

Production deployment workflows and CI/CD best practices for Spring Boot.

## When to Activate

- Setting up CI/CD pipelines (GitLab CI)
- Dockerizing a Spring Boot application
- Implementing health checks (Spring Boot Actuator)
- Preparing for an EKS (Kubernetes) production release

## Deployment Strategies

### Rolling Deployment (Kubernetes Default)

Replace instances gradually — old and new versions run simultaneously during rollout.
**Pros:** Zero downtime, gradual rollout.
**Cons:** Requires backward-compatible database changes (Liquibase expand/contract).

## Docker

### Multi-Stage Dockerfile (Spring Boot / Maven)

```dockerfile
# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17-focal AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Production image
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from builder
COPY --from=builder /app/target/*.jar app.jar

ENV TZ="Asia/Ho_Chi_Minh"
EXPOSE 8080 5005

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Best Practices

- Use specific version tags (`eclipse-temurin:17-jre-alpine`, not `latest`).
- Use multi-stage builds to avoid shipping Maven and source code to production.
- Run the Java process as a non-root user.
- Add Actuator HEALTHCHECK.

## Health Checks (Spring Boot Actuator)

Spring Boot Actuator provides built-in health and readiness endpoints suitable for Kubernetes.

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Kubernetes Probes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 5
```

## Environment Configuration

Use standard Spring Boot externalized configuration.

```bash
# Set via Kubernetes ConfigMap or Secrets
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mbal
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
```

## Rollback Strategy

- Database: Ensure Liquibase migrations are additive (never drop columns in the same release).
- Kubernetes: `kubectl rollout undo deployment/app`.

## Production Readiness Checklist

Before any production deployment:

### Application
- [ ] All tests pass (unit, integration via `mvn test`).
- [ ] SonarQube quality gates passed.
- [ ] No hardcoded secrets in `application.yml` or code.
- [ ] PII data is masked in logs (SLF4J configuration).
- [ ] Actuator endpoints are secured (except `/health`).

### Infrastructure
- [ ] Docker image builds reproducibly.
- [ ] Resource limits (CPU/Memory) set appropriately for JVM to avoid OOMKilled.
- [ ] JVM memory flags properly set (e.g., `-XX:MaxRAMPercentage=75.0`).
