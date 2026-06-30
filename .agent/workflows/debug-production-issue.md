---
description: Debug production issues using logs, traceId, Redis, DB, external APIs, idempotency, and minimal safe fix planning.
---

# Debug Production Issue

## Goal

Analyze a production issue safely from evidence before proposing any fix.

## Steps

1. Read AGENTS.md
2. Read .agent/rules/project-rules.md if available
3. Read the provided logs carefully
4. Identify traceId, requestId, transactionId, referenceNo, policyNo, claimNo, customerId, or phone if available
5. Mask PII when summarizing evidence
6. Identify the failing API, job, listener, scheduler, or integration flow
7. Trace the related code path: Controller/Consumer/Scheduler → Service → Repository → Client
8. Identify the exact failure point and exception cause
9. Check external API request/response behavior
10. Check Redis/cache state assumptions
11. Check DB state assumptions and transaction boundaries
12. Check retry behavior and idempotency handling
13. Check duplicate transaction, duplicate notification, duplicate callback, or duplicate e-form risks
14. Identify root cause with supporting evidence
15. Propose the minimum safe fix
16. Do not modify code unless explicitly asked

## Output Format

- Incident Summary
- Evidence From Logs
- Affected Flow
- Likely Root Cause
- Impact
- Redis/DB/Integration Findings
- Idempotency/Concurrency Risks
- Minimum Safe Fix Plan
- Files Likely Involved
- Tests / Checks To Run
- Risk Notess
