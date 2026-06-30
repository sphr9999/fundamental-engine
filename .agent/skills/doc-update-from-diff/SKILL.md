---
name: doc-update-from-diff
description: Cập nhật documentation dựa trên code diff. Buộc diff-bounded updates.
when_to_use: khi cập nhật documentation dựa trên code changes (bàn giao source mới)
version: 1.0.0
---

## Nguyên tắc
**CHỈ diff-bounded updates.** Mọi doc change PHẢI trace về 1 code change cụ thể.

## Quy trình
1. Đọc TOÀN BỘ diff TRƯỚC khi chạm doc
2. Liệt kê MỌI code change cần doc update
3. Xác định CHÍNH XÁC sections bị ảnh hưởng
4. Thay đổi TỐI THIỂU phản ánh đúng code change
5. Bảo toàn context, formatting, cross-references
6. Chạy verification
7. Self-review: mọi doc change trace được về diff?

## Module classification
- **HEAVY** (>40% files changed) → regenerate docs từ source mới
- **LIGHT** (<20% files changed) → targeted update (chỉ sửa sections)
- **UNCHANGED** → validate only

## Anti-Patterns
| Suy nghĩ | Thực tế |
|----------|---------|
| "Tiện tay improve luôn" | Scope creep |
| "Doc sai sẵn, sửa luôn" | Tạo issue riêng |
| "Diff nhỏ, skip đọc doc" | Phải đọc context |
| "Tạo lại cho nhanh" | Mất customizations |
