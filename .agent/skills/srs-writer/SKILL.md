---
name: srs-writer
description: Write Software Requirements Specification (SRS) optimized for Confluence and AI Agent context (Hybrid mode). Translates PRD or sequence diagrams into structural constraints, actors, transitions, and API contracts.
---

# SRS Writer

## ROLE

Senior System Analyst & Solution Architect.

Phân tích luồng nghiệp vụ, Sequence Diagram, hoặc PRD để viết tài liệu SRS.

Tài liệu Hybrid phục vụ 2 mục đích:
1. Con người (BA, Dev, QA, PO) đọc trên Confluence.
2. AI Agent dùng làm Context để maintain/enhance mà không phá luật cũ.

---

## INPUTS (Đọc trước khi viết)

Theo thứ tự ưu tiên:

1. **Controller Interface** — Xác định toàn bộ API endpoints, HTTP method, path, request/response DTO.
2. **Service Implementation** — Xác định business logic, downstream calls, validation.
3. **Sequence Diagram / PRD** — Nếu user cung cấp sơ đồ luồng hoặc tài liệu nghiệp vụ.
4. **AGENTS.md** — Đọc constraints về security, idempotency, audit.

Nếu user chỉ cung cấp Controller, đọc Service tương ứng để hiểu logic.
Nếu thiếu thông tin, dùng `{TODO: text}` để đánh dấu. KHÔNG bịa đặt.

---

## RULES

1. Mỗi bullet phải đi thẳng vào vấn đề, không viết lan man.
2. KHÔNG bịa đặt business rule. Dùng `{TODO: verify}` nếu chưa rõ.
3. Sơ đồ bắt buộc dùng Mermaid `sequenceDiagram`.
4. Liệt kê **toàn bộ** API có trong Controller vào phần Implementation Contract.
5. Dùng Markdown table khi liệt kê nhiều items cùng loại.
6. Ưu tiên nội dung copy trực tiếp lên Confluence.

---

## OUTPUT FORMAT

BẮT BUỘC có đúng 6 section theo thứ tự dưới đây.
Không thêm section ngoài danh sách này.

---

### Section 1. Meta Information

- **Feature Name:** Tên tính năng
- **Status:** Active / Draft
- **Related Epic/Jira:** Link hoặc `{TODO}`
- **Tech Stack/Services:** Các service/module tham gia
- **Primary Actor:** Đối tượng chính sử dụng tính năng

---

### Section 2. Business Intent

- **Vấn đề giải quyết:** 1-2 câu mô tả mục đích kinh doanh.
- **Business Flow:** Tóm tắt các bước user trải qua (dạng arrow flow `A -> B -> C`).
- **Glossary (Thuật ngữ):**

Bảng thuật ngữ domain, bắt buộc có nếu tính năng chứa từ viết tắt hoặc thuật ngữ chuyên ngành.

| Thuật ngữ | Giải thích |
|---|---|

---

### Section 3. Architecture & Actors

**3.1 Actors & Boundaries**
Liệt kê: User, Client App, Backend, Integration Layer, Core System.

**3.2 Sequence Diagram**
Mermaid `sequenceDiagram` với `autonumber`. Chỉ vẽ các bước chính (happy path).

---

### Section 4. Core Constraints & Business Rules

Liệt kê các bất biến (Invariants) mà code bắt buộc phải tuân thủ.
Phân loại theo nhóm:

1. **Ownership & AuthZ** — Quyền hạn, phân quyền truy cập.
2. **Data Integrity** — Các quy tắc không được vi phạm về dữ liệu.
3. **Stateless/Stateful** — Backend lưu trạng thái hay không, Source of Truth ở đâu.
4. **Idempotency** — Xử lý duplicate request (nếu có).

Chỉ liệt kê constraint thực sự tìm thấy trong code hoặc user cung cấp.

---

### Section 5. State Machine & Lifecycle

Liệt kê các trạng thái mà đối tượng chính trải qua.
Format: `STATE_CODE`: Mô tả ngắn.

Nếu trạng thái được quản lý bởi hệ thống khác (DP, Core), ghi rõ hệ thống nào master.

---

### Section 6. Implementation Contract

Liệt kê các thành phần code chịu trách nhiệm:

- **Controller:** Tên Interface + Impl class.
- **Service:** Tên Service Interface + Impl class.
- **Integration Client:** Tên các downstream client.
- **API Endpoints:** Bảng liệt kê toàn bộ API.

| # | Method | Path | Description | Payload / Params |
|---:|---|---|---|---|

Dùng table thay vì bullet list để Confluence hiển thị gọn gàng và dễ scan hơn.
