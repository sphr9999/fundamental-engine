# Feature: Screener

## Mục đích

Lọc và xếp hạng cổ phiếu theo các tiêu chí FA. Cho phép người dùng tìm nhanh cổ phiếu thoả mãn điều kiện.

## API

```
GET /internal/fa/screener
```

## Bộ lọc hỗ trợ

| Filter | Param | Ví dụ |
|---|---|---|
| Sàn giao dịch | `exchange` | `HOSE`, `HNX`, `UPCOM` |
| Xếp hạng FA | `rating` | `STRONG_FA`, `GOOD_FA`, `FAIR_FA`, `WEAK_FA`, `POOR_FA` |
| Điểm FA tối thiểu | `minFaScore` | `80`, `90`, `95` |
| Kỳ báo cáo | `period` | `2026Q1` |
| Batch cụ thể | `batchId` | `1001` |
| Phân trang | `page`, `pageSize` | `0`, `20` |

## Cách tính số trang

Backend trả về `totalCount` = tổng số ticker thoả điều kiện.

```
totalPages = ceil(totalCount / pageSize)
```

Frontend dùng giá trị này để render pagination controls.

## Query Backend

Screener dùng một JPQL query với JOIN để filter exchange + rating + minScore trong cùng một câu DB (đảm bảo phân trang chính xác):

```sql
SELECT s FROM FaScoreSnapshotEntity s
JOIN FaCompanyEntity c ON s.ticker = c.ticker
WHERE s.periodCode = :periodCode
  AND s.importBatchId = :importBatchId
  AND (:exchange IS NULL OR c.exchange = :exchange)
  AND (:rating IS NULL OR s.rating = :rating)
  AND (:minScore IS NULL OR s.overallScore >= :minScore)
ORDER BY s.overallScore DESC
```

> **Quan trọng**: Phải filter exchange ở DB trước khi phân trang. Nếu filter sau khi phân trang sẽ gây ra số item mỗi trang không ổn định (bug đã fix).

## Sort

Mặc định sort theo `overallScore DESC` (điểm FA cao nhất trước).

## Output fields

```json
{
  "ticker": "HPG",
  "companyName": "Hoa Phat Group",
  "exchange": "HOSE",
  "industry": "Thép và sản phẩm thép",
  "rating": "STRONG_FA",
  "overallScore": 92.0,
  "growthScore": 30.0,
  "profitabilityScore": 21.0,
  "valuationScore": 21.0,
  "dataQuality": "OK"
}
```

## Hạn chế Phase 1

- Chưa filter theo ngành (`industry`).
- Chưa filter theo metric cụ thể (ví dụ: `minPe < 15`, `minRevenueYoy > 20`).
- Chưa có multi-sort (chỉ sort theo FA Score).
- Chưa có export CSV.
