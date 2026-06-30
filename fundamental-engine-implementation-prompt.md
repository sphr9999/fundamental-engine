# Prompt triển khai cho Antigravity

Read `AGENTS.md` first.

Bạn là Senior Java/Spring Boot Architect + Financial Data Engineer.
Hãy implement project mới tên `fundamental-engine` theo đúng instruction trong `AGENTS.md`.

## Mục tiêu Phase 1

- Dựng Spring Boot service mới.
- Import file Excel KQKD production mẫu của các doanh nghiệp niêm yết Việt Nam.
- Chuẩn hóa dữ liệu vào PostgreSQL.
- Dùng Liquibase tạo schema.
- Tính các chỉ số FA cơ bản: Revenue YoY, NPAT YoY, QoQ, Gross Margin, Net Margin, Market Cap, P/E nếu đủ data, P/B.
- Tính FA score rule-based 0-100.
- Expose API nội bộ cho `eyeapp-backend`:
  - import Excel
  - import batch detail
  - quality report
  - ticker overview
  - ticker financials
  - ticker ratios
  - ticker score
  - screener search

## Không làm trong Phase 1

- Không làm ML prediction.
- Không làm trading bot.
- Không đưa ra khuyến nghị mua/bán trực tiếp.
- Không gọi `eybroker` từ `fundamental-engine`.
- Không làm frontend.
- Không crawl data tự động.

## Yêu cầu kỹ thuật

- Java 21 nếu môi trường hỗ trợ, nếu không thì Java 17.
- Spring Boot 3.x.
- Maven.
- PostgreSQL.
- Liquibase.
- Spring Data JPA.
- Apache POI để đọc Excel.
- BigDecimal cho mọi số liệu tài chính.
- Không convert blank/error cell thành 0.
- Mỗi lần import tạo một `fa_import_batch` mới.
- API read mặc định dùng latest successful batch.

## Luồng implement

1. Bootstrap Maven Spring Boot project.
2. Thêm `application.yml`, `application-local.yml`, `docker-compose.yml` cho PostgreSQL local.
3. Tạo Liquibase changelog core tables.
4. Tạo domain enums: `MetricCode`, `RatioCode`, `QualityStatus`, `ImportStatus`, `SectorModel`.
5. Tạo JPA entities và repositories.
6. Tạo Excel import module:
   - sheet alias resolver
   - period parser
   - cell value extractor
   - financial sheet parser
   - import orchestrator
7. Tạo ratio calculation service.
8. Tạo FA score calculation service.
9. Tạo controllers + DTOs.
10. Tạo unit tests.
11. Run `mvn clean test`.

## Báo cáo sau mỗi milestone

Trả về theo format:

```md
## Summary
## Files Changed
## Tests / Checks
## Risk Notes
```
