---
trigger: manual
---

# Agent Orchestration

## Available Agents

Located in `.agent/agents/`:

| Agent | Purpose | When to Use |
|-------|---------|-------------|
| planner | Implementation planning | Complex features, refactoring |
| java-reviewer | Java/Spring Boot code review | After writing Java code |
| java-build-resolver | Fix Java build errors | When Maven/Gradle build fails |
| security-reviewer | Security analysis | Before commits, auth/payment code |
| database-reviewer | Database/query review | JPA, Liquibase, SQL changes |
| tdd-guide | Test-driven development | New features, bug fixes |

## When to Use Agents

1. Complex feature requests → **planner**
2. Java code written/modified → **java-reviewer**
3. Build failures → **java-build-resolver**
4. Security-sensitive changes → **security-reviewer**
5. Database/migration changes → **database-reviewer**
6. New feature or bug fix → **tdd-guide**
