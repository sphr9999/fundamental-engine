---
name: enhancement-from-prd
description: >
  Analyze và implement enhancement dựa trên PRD/BRD từ BA.
  Full pipeline: analyze → design → implement → test → docs → review.
  Engine thực thi = ECC (/ecc:*). Map: /ecc:plan (decompose), agent delegation +
  /ecc:multi-execute (cross-module execute), /ecc:tdd (TDD), /ecc:verify + /ecc:quality-gate
  (verify), /ecc:code-review (review). Spec lifecycle do OpenSpec (/opsx:*) quản trị.
when_to_use: khi nhận PRD/BRD yêu cầu thay đổi/mở rộng chức năng hiện có
version: 3.0.0
---

## Nguyên tắc
1. **ĐỌC requirement TRƯỚC, code SAU**
2. **ANALYZE impact TRƯỚC, implement SAU**
3. **KHÔNG phá existing behavior** — backward compatibility là default
4. **FOLLOW platform conventions** (search-first khi cần tra pattern)
5. **TEST regression** — existing tests PHẢI pass
6. **UPDATE docs** — enhancement LUÔN thay đổi behavior

## ECC Strategy (map phase → surface)

| Phase | ECC surface | Vai trò |
|-------|-------------|---------|
| Analyze/Plan | **/ecc:plan** (planner) | PRD → Impact Analysis (12-question) → decompose tasks |
| Execute (cross-module) | **agent delegation** + **/ecc:multi-execute** | 1 subagent per module affected |
| TDD | **/ecc:tdd** (tdd-guide) | Nếu AC rõ → viết test từ AC trước implement |
| Verify | **/ecc:verify** + **/ecc:quality-gate** | AC, regression, docs completeness, coverage |
| Review | **/ecc:code-review** (code-reviewer) | Self-review design + implementation |

> **KHÔNG cần** ECC design (search-first/architect generate options) — PRD từ BA đã là spec.
> Spec/proposal/archive = **OpenSpec** (`/opsx:propose → /opsx:verify → /opsx:archive`).

## Phase 1 — Analyze (/ecc:plan)

```
/ecc:plan (planner agent):
  Input: PRD/BRD docs/enhancement-requests/{ENH-ID}.md
  Output: Impact Analysis (12-question) + decompose tasks

  Step 1: PRD → requirements + acceptance criteria
  Step 2: Identify modules affected → single vs cross-module
  Step 3: Đọc current docs (api-ref, code-structure, db-schema, flows)
  Step 4: Trả lời 12-question Impact Analysis
  Step 5: Design As-Is → To-Be (Mermaid)
  Step 6: Changes Required table
  Step 7: Backward compatibility
  Step 8: Test Plan

/ecc:code-review (self-review Design Doc TRƯỚC khi present):
  Check: impact analysis complete? changes table accurate? compatibility assessed?
```

## Phase 3 — Implement (agent delegation nếu cross-module)

### Single-module → sequential (không cần delegation)
```
1. DB migration   2. Entity/Repository   3. Service   4. Controller   5. Frontend   6. Config
```

### Cross-module → ECC agent delegation (/ecc:multi-execute)
```
Orchestrator (Opus): giữ design doc, coordinate
  Subagent 1: Module A — implement + /ecc:tdd → DONE/DONE_WITH_CONCERNS/BLOCKED
  Subagent 2: Module B — implement + /ecc:tdd → DONE/...
  Subagent 3: Integration + Cross-cutting — Feign, Kafka config, integration tests → DONE/...
  Subagent 4: Documentation — api-ref, db-schema, code-structure, error-handling, diagrams, flows → DONE
```

## Phase 4-5 — Verify (/ecc:verify + /ecc:quality-gate)

```
1. Run tests: unit + integration → ALL pass?
2. Acceptance criteria: verify mỗi AC-x từ PRD
3. Backward compatibility: existing API contracts intact?
4. Docs: mọi changed files reflected?
5. File size: 0 files >15KB?
6. verify-docs.sh: passes?
Nếu FAIL bất kỳ → fix qua /ecc:tdd trước khi report DONE
```

## Phase 6 — Review (/ecc:code-review) → OpenSpec verify/archive

```
Stage 1: Self-review — Design doc followed? Conventions? All subagents DONE?
Stage 2: Review Checklist — Implementation/Tests/Docs/AC ✅/❌
→ /opsx:verify {name} → PASS → /opsx:archive {name}
```

## Anti-patterns
- KHÔNG implement mà không output Design Doc trước
- KHÔNG skip agent delegation cho cross-module changes (sẽ miss side effects)
- KHÔNG skip /ecc:verify — "nó đơn giản" là lý do phổ biến nhất gây regression
- KHÔNG update code mà không update docs
