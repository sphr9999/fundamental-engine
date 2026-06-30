# fundamental-engine — Documentation Index

> Engine phân tích cơ bản (FA) cho thị trường chứng khoán Việt Nam.  
> Là upstream engine trong hệ sinh thái `eyelanding / eyeapp`.

## Tài liệu

| File | Mô tả |
|---|---|
| [architecture.md](./architecture.md) | Kiến trúc tổng thể, luồng dữ liệu, package layout |
| [database-schema.md](./database-schema.md) | Thiết kế database, schema chi tiết, index |
| [api-spec.md](./api-spec.md) | Đặc tả toàn bộ API endpoints |
| [features/excel-import.md](./features/excel-import.md) | Feature: Import Excel workbook |
| [features/fa-score.md](./features/fa-score.md) | Feature: Tính điểm FA Score (rule-based) |
| [features/screener.md](./features/screener.md) | Feature: Bộ lọc cổ phiếu (Screener) |
| [features/ticker-dashboard.md](./features/ticker-dashboard.md) | Feature: Dashboard chi tiết từng cổ phiếu |
| [roadmap-phase2.md](./roadmap-phase2.md) | Kế hoạch mở rộng Phase 2 (Balance Sheet, Cash Flow, Dividends) |

## Phiên bản hiện tại

- **Phase**: 1 (MVP)
- **Data coverage**: Income Statement (partial) + Market Data
- **Tickers**: ~1.600 (HOSE, HNX, UPCOM)
- **Periods**: Q1.2024 → Q1.2026 (từ Excel workbook)
- **Port**: `8088`
- **Base path**: `/internal/fa`
