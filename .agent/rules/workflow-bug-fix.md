---
description: "Workflow chi tiết cho Bug Fix — human-reported và automated"
globs: ["docs/quality-reports/*bug*", "docs/bugs/**", "reports/**"]
---

# Workflow: Bug Fix (Step-by-step BẮT BUỘC)

> **Engine**: ECC (`/ecc:*`) thực thi · OpenSpec (`/opsx:*`) quản trị spec.

## Luồng 1: Bug từ con người (tester/user)

```
STEP 1: /project:review-bug → score ≥75%
         │
STEP 2: docs/modes/bug-fix-mode.md
  ├── Multi-Hypothesis Investigation (3 góc)
  ├── /ecc:tdd (tdd-guide): write failing test → fix → test pass
  ├── /ecc:verify: 5-step gate trước khi claim done (FRESH test evidence)
  └── Behavior-change-matrix: 9 câu YES/NO
         │
STEP 3: OpenSpec (CHỈ nếu behavior thay đổi)
  ├── Tất cả NO → Skip OpenSpec → DONE
  └── Bất kỳ YES → /opsx:propose → /opsx:archive → Update docs → DONE
```

## Luồng 2: Bug kỹ thuật tự động (ELK APM → 5xx)

```
STEP 1: Agent + MCP đọc ELK → tạo tech-bug-auto-template.md → bypass quality gate
         │
STEP 2: Scope gate (trong template §10)
  ├── Loại lỗi trong allowed list (null check, validation, exception handling) → STEP 3
  └── Loại lỗi trong escalate list (business logic, auth, DB schema) → tạo Jira → STOP
         │
STEP 3: bug-fix-mode.md → /ecc:tdd → /ecc:verify → behavior-change-matrix → PR draft [AUTO-FIX]
         │
STEP 4: Chuyển bug report từ /pending/ → /done/ → thông báo UAT
```

## Critical Business Path
Modules Policy, Claim, Payment, Premium: LUÔN human review, KHÔNG auto-merge.
Automated fix pipeline nếu đụng modules này → escalate to human bắt buộc.
