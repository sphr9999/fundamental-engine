---
description: Analyze an existing Spring Boot API flow from controller to service, repository, integrations, Redis, DB, and side effects.
---

# Analyze API Flow

## Goal

Analyze an existing API flow safely and accurately before making any code changes.

## Steps

1. Read AGENTS.md
2. Read .agent/rules/project-rules.md if available
3. Identify the API endpoint, HTTP method, path, controller, and request/response DTOs
4. Trace the full flow: Controller → Service → Repository → Client/Integration → Mapper
5. Identify validation rules and validation order
6. Identify authentication and authorization checks
7. Identify Redis/cache usage and side effects
8. Identify database reads/writes and transaction boundaries
9. Identify external API calls, request mapping, response mapping, retry behavior, and fallback behavior
10. Identify logging points and check for PII/token/OTP/payment data leakage
11. Identify idempotency and concurrency risks
12. Identify backward compatibility risks in API contracts
13. Summarize the flow clearly
14. Do not modify code unless explicitly asked

## Output Format

- API Summary
- Endpoint
- Request/Response DTOs
- Main Flow
- Validation Rules
- Auth/AuthZ
- Redis/DB Side Effects
- External Integrations
- Transaction Boundaries
- Idempotency/Concurrency Risks
- PII/Security Risks
- Open Questions / TODO verify business rule
