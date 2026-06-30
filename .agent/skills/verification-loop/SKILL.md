---
name: verification-loop
description: "A comprehensive verification system for Spring Boot code sessions."
origin: ECC
---

# Verification Loop Skill

A comprehensive verification system for AI-assisted development sessions.

## When to Use

Invoke this skill:
- After completing a feature or significant code change
- Before creating a PR
- When you want to ensure quality gates pass
- After refactoring

## Verification Phases

### Phase 1: Build Verification
```bash
# Check if project compiles successfully
mvn clean compile -DskipTests
```

If build fails, STOP and fix before continuing.

### Phase 2: Lint / Static Analysis
```bash
# Run SonarQube or Checkstyle (if configured)
mvn checkstyle:check
```

### Phase 3: Test Suite
```bash
# Run unit and integration tests
mvn test -pl <affected-module>

# If full verification is needed
mvn test
```

Report:
- Total tests run
- Passed / Failed

### Phase 4: Security Scan (Code Level)
```bash
# Check for secrets or PII logging
grep -rn "sk-" --include="*.java" . 2>/dev/null | head -10
grep -rn "password" --include="*.yml" . 2>/dev/null | head -10
```

### Phase 5: Diff Review
```bash
# Show what changed
git diff --stat
git diff HEAD~1 --name-only
```

Review each changed file for:
- Unintended changes
- Missing error handling
- Potential edge cases
- Strict adherence to project architecture

## Output Format

After running all phases, produce a verification report:

```
VERIFICATION REPORT
==================

Build:     [PASS/FAIL]
Lint:      [PASS/FAIL]
Tests:     [PASS/FAIL] (X/Y passed)
Security:  [PASS/FAIL] (X issues)
Diff:      [X files changed]

Overall:   [READY/NOT READY] for PR

Issues to Fix:
1. ...
2. ...
```

## Continuous Mode

For long sessions, run verification every 15 minutes or after major changes:

```markdown
Set a mental checkpoint:
- After completing each service method
- After modifying a controller
- Before moving to next task
```
