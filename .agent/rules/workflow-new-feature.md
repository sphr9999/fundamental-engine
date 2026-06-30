---
description: "Workflow chi tiết cho New Feature — step-by-step BẮT BUỘC"
globs: ["docs/quality-reports/*feature*", "openspec/changes/**"]
---

# Workflow: New Feature (Step-by-step BẮT BUỘC)

> **Engine**: ECC (`/ecc:*`) thực thi · OpenSpec (`/opsx:*`) quản trị spec. Hai engine trực giao.

## ⚠️ QUY TẮC CHUYỂN TIẾP CỨNG

- ECC design (`search-first` + `architect`) XONG → ⛔ KHÔNG gọi `/ecc:plan` decompose → ✅ GỌI /opsx:propose
- OpenSpec /opsx:propose XONG → ⛔ KHÔNG gọi /opsx:apply → ✅ CHỜ Human review → GỌI `/ecc:plan` decompose
- `/ecc:plan` decompose XONG → ✅ GỌI ECC execute (agent delegation + `/ecc:tdd` per task)

## Steps

```
STEP 1: Input Quality Gate
  ├── /project:review-feature
  ├── Thoát khi: score ≥75%
  └── Output: docs/quality-reports/{date}-{id}-quality-report.md
         │
STEP 2: ECC Design (research + architecture)
  ├── search-first skill (tra existing pattern/lib trong codebase + platform docs TRƯỚC khi tự viết)
  ├── architect agent → sinh 2-3 option kiến trúc + recommend
  ├── Output: lamb-docs/docs/design/{date}-{name}-design.md
  ├── ⛔ KHÔNG gọi /ecc:plan (decompose) ở bước này
  └── ✅ Chuyển STEP 3
         │
STEP 3: OpenSpec Propose
  ├── /opsx:propose {name}
  ├── Input: ECC design output + quality report
  ├── Output: proposal.md + design.md + tasks.md + delta specs
  ├── ⛔ KHÔNG gọi /opsx:apply hoặc /opsx:continue
  └── ✅ Chờ Human Review
         │
STEP 3.5: Human Review + Approve
  ├── Reject → quay STEP 2 hoặc STEP 3
  └── Approve → chuyển STEP 4
         │
STEP 4: ECC Plan (decompose)
  ├── /ecc:plan {name}  (planner agent — restate + risk + step-by-step, chờ confirm trước khi code)
  ├── Input: OpenSpec tasks.md
  ├── Chia thành bite-sized tasks (2-5 phút)
  └── ✅ Chuyển STEP 5
         │
STEP 5: ECC Execute
  ├── Agent delegation: 1 subagent per task/layer (/ecc:multi-execute nếu chạy song song)
  ├── Mỗi task → /ecc:tdd (tdd-guide): RED → GREEN → REFACTOR, coverage 80%+
  ├── /ecc:code-review (code-reviewer) giữa các tasks
  └── ✅ Tất cả tasks done → STEP 6
         │
STEP 6: Verify (ECC + OpenSpec)
  ├── /ecc:verify + /ecc:quality-gate  (platform conventions + tests + coverage)
  ├── /opsx:verify {name}  (implementation vs delta specs + Behavior-change-matrix 9 câu)
  ├── FAIL → quay STEP 5 (fix qua /ecc:tdd)
  └── PASS → STEP 7
         │
STEP 7: Archive + Docs Update
  ├── /opsx:archive {name}
  ├── Update per-module docs nếu cần
  ├── Update CLAUDE.md Module Registry nếu module mới
  └── DONE — thông báo ready for UAT
```
