---
description: Implement scoped Spring Boot changes safely while preserving API contracts, architecture, security, and idempotency.
---

# Implement Change Safely

## Goal

Make the smallest safe code change that satisfies the requirement without breaking existing contracts or architecture.

## Steps

1. Read AGENTS.md
2. Read .agent/rules/project-rules.md if available
3. Understand the requirement and identify unclear business rules
4. Analyze existing implementation before editing
5. Identify affected modules, APIs, DTOs, services, repositories, clients, Redis keys, DB tables, and integrations
6. Preserve API contracts unless explicitly asked
7. Preserve existing response wrapper and error format
8. Keep controllers thin
9. Put business logic in service layer
10. Keep repository only for persistence/query logic
11. Use client layer for external API calls
12. Keep transaction scope small
13. Avoid external API calls inside DB transactions unless existing design requires it
14. Preserve audit fields, traceId, transactionId, and referenceNo
15. Check PII/token/OTP/payment logging risks
16. Check idempotency and concurrency risks
17. Avoid unrelated refactor
18. Add or update tests when appropriate
19. Run relevant checks if possible
20. Summarize changes and risks

## Output Format

- Summary
- Files Changed
- Implementation Notes
- Tests / Checks
- Risk Notes
- TODO verify business rule
