---
name: docker-patterns
description: Docker and Docker Compose patterns for local development, networking, volume strategies, and multi-service orchestration for Java Spring Boot.
origin: ECC
---

# Docker Patterns

Docker and Docker Compose best practices for containerized Spring Boot development.

## When to Activate

- Setting up Docker Compose for local development (PostgreSQL, Redis, Kafka)
- Designing multi-container architectures
- Troubleshooting container networking or volume issues

## Docker Compose for Local Development

### Standard Spring Boot Stack

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
      - "5005:5005"   # JVM Remote Debug port
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mbal_dev
      - SPRING_REDIS_HOST=redis
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      # Enable remote debugging
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started

  db:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: devuser
      POSTGRES_PASSWORD: devpassword
      POSTGRES_DB: mbal_dev
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U devuser"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data

  kafka:
    image: bitnami/kafka:3.4
    ports:
      - "9092:9092"
    environment:
      - KAFKA_ENABLE_KRAFT=yes
      - KAFKA_CFG_PROCESS_ROLES=broker,controller
      - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
      - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092

volumes:
  pgdata:
  redisdata:
```

## Debugging Java Containers

### Connect IDE to Container

1. Ensure the port `5005` is mapped in `docker-compose.yml`.
2. Ensure `JAVA_TOOL_OPTIONS` includes `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`.
3. Use IntelliJ IDEA / Eclipse Remote JVM Debug configuration to connect to `localhost:5005`.

### View Logs

```bash
docker compose logs -f app           # Follow Spring Boot logs
docker compose logs --tail=50 db     # Last 50 lines from Postgres
```
