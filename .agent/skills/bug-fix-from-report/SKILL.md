---
name: bug-fix-from-report
description: >
  Investigate và fix production bugs dựa trên bug report.
  Buộc Multi-Hypothesis investigation (3 góc: execution path, business logic, data path).
  MANDATORY: Tìm NHIỀU root causes trước khi kết luận — không dừng ở cái đầu tiên.
  MANDATORY: /ecc:verify (verification-loop) sau mỗi fix.
when_to_use: khi xử lý bug report từ production
version: 3.0.0
---

## Nguyên tắc
1. **INVESTIGATE trước, FIX sau**
2. **ĐỌC docs trước source code**
3. **CHECK known-issues trước**
4. **MULTI-HYPOTHESIS** — KHÔNG dừng ở root cause đầu tiên, kiểm tra 3 góc
5. **MINIMAL fix** — sửa root cause, không refactor xung quanh
6. **TEST fix** — PHẢI có fresh test evidence
7. **TRA BẢNG behavior-change-matrix** để quyết định update docs
8. **`/ecc:verify` (verification-loop, 5-step gate)** — Iron Law

## Reading order
```
1. docs/bugs/{BUG-ID}.md                              ← Symptom
2. docs/architecture/SUMMARY.md                       ← Identify module
3. docs/architecture/{module}/error-handling.md        ← Error patterns
4. docs/architecture/{module}/api-reference*.md        ← Endpoint details
5. docs/architecture/known-issues/{domain}.md          ← Đã biết?
6. docs/architecture/{module}/code-structure.md        ← Class hierarchy + Debug Entry Points
7. docs/architecture/cross-cutting-flows/{domain}.md   ← Cross-layer
8. docs/architecture/dependency-map/{domain}.json      ← Impact scope
```

## ⚠️ Multi-Hypothesis Investigation (BẮT BUỘC)

Khi tìm được root cause đầu tiên → CHƯA kết luận → kiểm tra 3 góc:

- [ ] **Góc 1 — EXECUTION PATH**: Trace call flow, tìm unhandled exception/NPE/FeignException
- [ ] **Góc 2 — BUSINESS LOGIC**: So sánh pattern across fields/methods, tìm missing validation
- [ ] **Góc 3 — DATA PATH**: Liệt kê data scenarios, tìm multiple trigger paths

### KHÔNG ĐƯỢC dừng sớm

| AI muốn nói... | Phải làm gì |
|----------------|-------------|
| "Đã tìm được root cause" | Kiểm tra 2 góc còn lại |
| "Root cause rõ ràng" | Rõ ràng ≠ duy nhất |
| "Cùng symptom nên cùng cause" | 1 symptom có thể từ nhiều paths |

## TDD option (khuyến khích)

Nếu bug reproducible bằng test:
- [ ] Viết regression test TRƯỚC fix → confirm FAIL
- [ ] Implement fix
- [ ] Run test → confirm PASS

## Output format bắt buộc

```
## Root Cause Analysis — Multi-Hypothesis

### Hypothesis 1: {tên} — Confidence: HIGH/MEDIUM/LOW
- Module: | Layer: | File: | Function: | Cause:

### Hypothesis 2: {tên} — Confidence: HIGH/MEDIUM/LOW
- Module: | Layer: | File: | Function: | Cause:

### Kết luận: {N} root causes xác nhận → fix TẤT CẢ

## Proposed Fix (per root cause)
- H1: {approach, files, risk}
- H2: {approach, files, risk}

## Impact Analysis
- Endpoints: | DB: | Other modules:

## Investigation Completeness
- [ ] Execution path checked ✅
- [ ] Business logic checked ✅
- [ ] Data path checked ✅
```

## Verification Gate — sau khi fix

- [ ] **`/ecc:verify` (verification-loop, 5-step gate)**
- [ ] 5-step gate: IDENTIFY → RUN → READ → VERIFY → ONLY THEN claim done
- [ ] Verify TỪNG root cause riêng + combined regression
- [ ] KHÔNG ĐƯỢC rationalize: "should work", "I'm confident", "trivial change"

## Anti-patterns
- KHÔNG sửa code mà không trace root cause
- KHÔNG dừng ở root cause đầu tiên mà không kiểm tra 3 góc
- KHÔNG sửa nhiều files khi root cause ở 1 file
- KHÔNG skip test
- KHÔNG phán đoán "behavior không đổi" → PHẢI tra 9-question matrix
- KHÔNG claim "fixed" mà không chạy test FRESH ngay trước claim
