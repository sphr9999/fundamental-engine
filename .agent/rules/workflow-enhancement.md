---
description: "Workflow chi tiết cho Enhancement — step-by-step BẮT BUỘC"
globs: ["docs/quality-reports/*enhance*", "openspec/changes/**"]
---

# Workflow: Enhancement (Step-by-step BẮT BUỘC)

> **Engine**: ECC (`/ecc:*`) thực thi · OpenSpec (`/opsx:*`) quản trị spec.

## ⚠️ Enhancement KHÔNG cần ECC design (search-first/architect) — đi thẳng OpenSpec propose

PRD/BRD từ BA = spec đã có. Chỉ chạy `search-first` nhẹ nếu cần tra pattern hiện hữu, KHÔNG brainstorm kiến trúc mới.

```
STEP 1: Quality Gate → /project:review-enhance → score ≥75%
         │
STEP 2: /opsx:propose {name}
  ├── ⛔ KHÔNG gọi /opsx:apply
  └── ✅ Chờ Human Review → approve
         │
STEP 3: /ecc:plan {name}  (planner — decompose OpenSpec tasks.md thành bite-sized tasks)
         │
STEP 4: ECC execute (agent delegation per module; /ecc:multi-execute nếu cross-module) + /ecc:tdd per task
         │
STEP 5: /ecc:verify + /ecc:quality-gate → /opsx:verify → PASS? → /opsx:archive → Update docs → DONE
```
