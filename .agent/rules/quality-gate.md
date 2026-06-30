---
trigger: manual
description: "Input Quality Gate — chấm điểm work request trước khi implement"
globs: ["docs/quality-reports/**"]
---

# Input Quality Gate Rules — LAMB Platform

## Quy tắc
- MỌI work request (bug, enhance, CR, feature) PHẢI qua quality gate TRƯỚC khi implement
- Chỉ proceed khi score ≥75% (PASS)
- Ngoại lệ: automated tech bugs từ ELK APM → bypass quality gate

## Phân loại 4 loại request

| Signal | Loại | Rubric |
|--------|------|--------|
| Lỗi, bug, error, crash | BUG | Bug Report (9 dims / 100đ) |
| Cải thiện, thêm field, update UI | ENHANCEMENT | Enhancement PRD (11 dims / 100đ) |
| Thông tư, eBao đổi API, đổi config | CHANGE_REQUEST | CR (11 dims / 100đ) |
| Tính năng mới, module mới | NEW_FEATURE | Feature Request (12 dims / 100đ) |

## Scoring Quick Reference

### Bug Report — Top 3 Critical Dimensions
| Dimension | Điểm | Ghi chú |
|-----------|------|---------|
| Steps to Reproduce | 25đ | ✅ CRITICAL — phải follow được |
| Observed Behavior | 20đ | ✅ CRITICAL — error message cụ thể |
| Expected Behavior | 15đ | ✅ CRITICAL — cái đúng phải ra sao |

> Nếu S2R=0 VÀ OB=0 → AUTO REJECT bất kể tổng điểm.

### Feature Request — Top 4 Critical Dimensions
| Dimension | Điểm | Ghi chú |
|-----------|------|---------|
| Business Context | 15đ | ✅ CRITICAL — TẠI SAO cần |
| User Journeys | 15đ | ✅ CRITICAL — Actor → action → result |
| Success Criteria | 10đ | ✅ CRITICAL — đo thành công bằng gì |
| Scope Boundaries | 10đ | ✅ CRITICAL — in/out scope rõ ràng |

> Nếu Context=0 VÀ Journeys=0 → AUTO REJECT.

## Cross-System Detection

Nếu request liên quan ≥2 hệ thống (LAMB + DP, LAMB + eBao):
```
Final Score = (Rubric gốc × 0.7) + (Cross-System Addon × 0.3)

Cross-System Addon (5 dims / 100đ):
  X1: System Trace Completeness    (25đ)
  X2: Root Cause / Owner ID       (20đ)
  X3: Dependency Order             (20đ)
  X4: Integration Contract Clarity (20đ)
  X5: Cross-System Edge Cases      (15đ)
```

## Decision Gate

| Tổng điểm | Quyết định | Hành động |
|-----------|-----------|----------|
| ≥75% | ✅ PASS | Xuất report → bắt đầu design/implement |
| 55-74% | ⚠️ ENRICH | Auto-enrich từ codebase → re-score |
| <55% | ❌ REJECT | Liệt kê items thiếu → requester bổ sung |

## File output
- Path: `docs/quality-reports/{date}-{id}-quality-report.md`
- Re-score: append -v2, -v3

## References

- Full rubric chi tiết: `ECC/.claude/skills/input-quality-gate/SKILL.md`
- Evaluation criteria: `docs/superpowers/specs/evaluation-criteria.md`
