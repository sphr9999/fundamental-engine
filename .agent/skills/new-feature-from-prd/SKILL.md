---
name: new-feature-from-prd
description: >
  Design và implement feature mới dựa trên PRD/BRD từ BA.
  Full pipeline: research/design → architecture → implement → test → create docs → review.
  Engine thực thi = ECC (/ecc:*). Map: search-first+architect (design), /ecc:plan (decompose),
  agent delegation + /ecc:multi-execute (execute), /ecc:tdd (TDD), /ecc:verify + /ecc:quality-gate
  (verify), /ecc:code-review (review). Spec lifecycle do OpenSpec (/opsx:*) quản trị.
when_to_use: khi nhận PRD/BRD yêu cầu xây dựng chức năng hoàn toàn mới
version: 3.0.0
---

## Nguyên tắc
1. **RESEARCH + DESIGN architecture TRƯỚC, implement SAU**
2. **FOLLOW platform conventions** — KHÔNG invent pattern mới (search-first)
3. **INTEGRATE đúng** — Feign, Kafka, auth, config, error format
4. **FULL TEST** — unit + integration + acceptance criteria (TDD, coverage 80%+)
5. **CREATE docs** — feature mới = tạo docs MỚI

## ECC Strategy (map phase → surface)

| Phase | ECC surface | Vai trò |
|-------|-------------|---------|
| Design (research) | **search-first** skill + **architect** agent | Tra pattern/lib hiện hữu; sinh 2-3 option kiến trúc A/B/C |
| Plan (decompose) | **/ecc:plan** (planner agent) | Tasks.md (OpenSpec) → bite-sized tasks, chờ confirm |
| Execute | **agent delegation** + **/ecc:multi-execute** | 1 subagent per layer (DB, Service, API, FE, Test, Docs) |
| TDD | **/ecc:tdd** (tdd-guide) | Viết test từ AC/user stories TRƯỚC implement, RED→GREEN→REFACTOR |
| Verify | **/ecc:verify** + **/ecc:quality-gate** | Platform conventions, integration, docs, coverage |
| Review | **/ecc:code-review** (code-reviewer) | Review architecture (design) + integration (sau implement) |

> Spec/proposal/archive = **OpenSpec** (`/opsx:propose → /opsx:verify → /opsx:archive`), KHÔNG thuộc ECC.

## Phase 1 — Design (search-first + architect)

```
search-first skill:
  Input: PRD/BRD + platform SUMMARY + dependency-map
  Do: tra existing module/pattern/lib có thể tái dùng TRƯỚC khi đề xuất tạo mới

architect agent → 2-3 option kiến trúc:
  Option A: New standalone microservice  (Pros: isolated, independent deploy / Cons: thêm infra, thêm Feign)
  Option B: Extend existing module       (Pros: ít infra, shared DB / Cons: module phình to, coupling)
  Option C: Hybrid (nếu có)

Output: docs/ecc/design/{date}-{name}-design.md
  Step 1: PRD → user stories + AC + functional requirements
  Step 2: Đọc platform conventions (error-patterns, thread-async, testing-patterns)
  Step 3: Evaluate options → recommend
  Step 4: API design (endpoints table)
  Step 5: Data Model (Mermaid ER)
  Step 6: Sequence Diagram (main flow)
  Step 7: Integration Points
  Step 8: Config Properties + Error Codes (theo platform format)
  Step 9: Implementation Plan (file-by-file)
  Step 10: Test Plan
  Step 11: Backward Compatibility + Rollback Plan
  Step 12: Verify Platform Convention Checklist (10 items)
```

## Phase 2 — Review Design (/ecc:code-review)

```
Stage 1: Self-review Architecture Design
  - Platform conventions 10/10? ErrorModel? Config externalized?
  - ErrorDecoder mỗi Feign client? Health endpoint? Liquibase (KHÔNG JPA auto-DDL)?
Stage 2: Present Design Doc cho user → chờ approval → /opsx:propose
```

## Phase 3 — Implement (/ecc:tdd + agent delegation)

### TDD (/ecc:tdd — viết test TRƯỚC code)
```
1. AC trong PRD → acceptance tests (fail — RED)
2. API Design → controller tests (MockMvc)
3. Business Rules → service tests (Mockito)
4. Implement code → GREEN
5. Refactor → tests vẫn pass. Coverage 80%+.
```

### Agent delegation (parallel per layer — /ecc:multi-execute)
```
Orchestrator (Opus): giữ Design Doc + TDD test specs
  Subagent 1: Data Layer — Liquibase migration, Entity, Repository → repo tests
  Subagent 2: Service Layer — interface + impl, business logic → /ecc:tdd service tests
  Subagent 3: API Layer — Controller, DTO, validation, error handling, Swagger, Security → /ecc:tdd controller tests
  Subagent 4: Integration — Feign + ErrorDecoder, Kafka, config (application.yml) → integration tests
  Subagent 5: Frontend/Mobile (nếu có UI) — components, pages, routes → frontend tests
  Subagent 6: Documentation — full doc set (_templates/) + shared docs (SUMMARY, dep-map, flows, CLAUDE.md)
```

## Phase 4-5 — Verify (/ecc:verify + /ecc:quality-gate)

Platform Convention Checklist (10 items — đặc thù dự án, ECC không tự biết):
```
1. ErrorModel format        6. Liquibase migration
2. 9-digit error code       7. Test pattern MockMvc/Mockito
3. Config naming pattern     8. Feign ErrorDecoder
4. API path /api/v1/{res}   9. MDC/tracing logging
5. Keycloak + Spring Sec    10. Async thread-async conventions

Functional: ALL TDD tests pass? Integration? AC verified? Health endpoint? Swagger?
Docs: full doc set? CLAUDE.md registry? dependency-map? File size ≤15KB? verify-docs.sh?
```

## Phase 6 — Integration Review (/ecc:code-review) → OpenSpec verify/archive

```
Stage 1: Self-review — Architecture implemented as approved? ALL subagents DONE? Conventions 10/10?
Stage 2: Integration Review Checklist — Architecture/Integration/Tests/Docs/Operations ✅/❌
→ /opsx:verify {name} → PASS → /opsx:archive {name}
```

## Decision: New module hay extend existing?

| Signal | New module | Extend existing |
|--------|-----------|-----------------|
| Domain mới (loyalty, chat...) | ✅ | |
| Sub-domain của module đã có | | ✅ |
| Cần DB riêng | ✅ | |
| Dùng chung DB | | ✅ |
| Cần deploy riêng | ✅ | |
| Chỉ thêm API + logic | | ✅ |

## Anti-patterns
- KHÔNG tạo module mới khi extend đủ
- KHÔNG dùng error format khác platform
- KHÔNG hardcode config
- KHÔNG skip ErrorDecoder cho Feign
- KHÔNG skip Liquibase (KHÔNG dùng JPA auto-DDL)
- KHÔNG deploy mà không có health endpoint
- KHÔNG tạo module mà không tạo docs
- KHÔNG skip /ecc:tdd "cho nhanh" — test-first bắt logic errors sớm
