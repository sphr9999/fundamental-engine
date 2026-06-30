---
trigger: manual
---

# Project Rules

Read AGENTS.md first.

This is a Java Spring Boot 2.6.7 Maven multi-module backend project.

Always follow:

- Thin Controller
- Business logic in Service
- Repository only for persistence
- Liquibase only for DB changes
- Never log PII, token, OTP, CCCD, phone, payment data
- Preserve existing API contracts
- Do not modify unrelated modules
- Ask before destructive commands, commit, push, migration, dependency changes
