# API Specification — fundamental-engine

## Base

- **Base URL (internal)**: `http://fundamental-engine:8088/internal/fa`
- **Content-Type**: `application/json` (trừ upload: `multipart/form-data`)
- **Authentication**: Internal network only (Phase 1 — no auth)
- **Swagger UI**: `http://localhost:8088/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8088/v3/api-docs`

---

## 1. Import APIs

### `POST /internal/fa/import-batches/excel/preview`

Preview workbook trước khi import. **Không lưu dữ liệu**.

**Request** (`multipart/form-data`):

| Field | Required | Description |
|---|---|---|
| `file` | ✅ | File `.xlsx` |
| `reportPeriod` | ❌ | e.g. `2026Q1` |

**Response `200`**:

```json
{
  "fileName": "TVI - File KQKD Q1.2026.xlsx",
  "reportPeriod": "2026Q1",
  "detectedSheets": [
    {
      "sheetName": "Doanh thu",
      "logicalSheet": "REVENUE",
      "detectedTickers": 1630,
      "detectedPeriods": ["2024Q1", "2024Q2", "2024Q3", "2024Q4", "2025Q1", "2026Q1"],
      "sampleRows": 3,
      "warnings": []
    }
  ],
  "totalSheets": 9,
  "unmappedSheets": ["Dashboard", "Notes"]
}
```

---

### `POST /internal/fa/import-batches/excel`

Import toàn bộ workbook. Chạy **synchronous** ở Phase 1.

**Request** (`multipart/form-data`):

| Field | Required | Description |
|---|---|---|
| `file` | ✅ | File `.xlsx` |
| `reportPeriod` | ❌ | Kỳ báo cáo chính, e.g. `2026Q1` |
| `importedBy` | ❌ | Username/email người import |

**Response `200`**:

```json
{
  "batchId": 1001,
  "status": "SUCCESS",
  "sourceFileName": "TVI - File KQKD Q1.2026.xlsx",
  "sourceFileChecksum": "sha256:abc123...",
  "reportPeriod": "2026Q1",
  "totalRows": 1630,
  "successRows": 1540,
  "warningRows": 67,
  "errorRows": 23,
  "startedAt": "2026-06-30T10:00:00",
  "finishedAt": "2026-06-30T10:03:00"
}
```

**Response `500`**: Import thất bại nghiêm trọng.

---

### `GET /internal/fa/import-batches/{batchId}`

Lấy trạng thái của một batch import.

**Path param**: `batchId` (Long)

**Response `200`**: Giống response của POST import bên trên.

**Response `404`**: Batch không tồn tại.

---

### `GET /internal/fa/import-batches/{batchId}/quality-report`

Xem báo cáo chất lượng dữ liệu của một batch.

**Query params**:

| Param | Default | Description |
|---|---|---|
| `severity` | (all) | `ERROR` / `WARN` / `INFO` |
| `limit` | `200` | Số issue trả về tối đa |

**Response `200`**:

```json
{
  "batchId": 1001,
  "summary": {
    "ERROR": 23,
    "WARN": 67,
    "INFO": 120
  },
  "issues": [
    {
      "ticker": "ABC",
      "periodCode": "2026Q1",
      "metricCode": "REVENUE",
      "issueType": "FORMULA_ERROR",
      "severity": "ERROR",
      "sourceSheet": "Doanh thu",
      "sourceCell": "C15",
      "message": "Formula error #DIV/0! at cell C15"
    }
  ]
}
```

---

## 2. Ticker APIs

### `GET /internal/fa/tickers/{ticker}/overview`

Tổng quan FA cho một mã cổ phiếu.

**Path param**: `ticker` (String, e.g. `HPG`)

**Query params**:

| Param | Default | Description |
|---|---|---|
| `period` | `2026Q1` | Kỳ báo cáo |
| `batchId` | (latest SUCCESS) | Import batch cụ thể |

**Response `200`**:

```json
{
  "ticker": "HPG",
  "companyName": "Hoa Phat Group",
  "exchange": "HOSE",
  "industry": "Thép và sản phẩm thép",
  "period": "2026Q1",
  "price": 28500,
  "pb": 1.54,
  "peTtm": 9.82,
  "marketCap": 207238000000000,
  "faScore": 92.0,
  "rating": "STRONG_FA",
  "growthScore": 30.0,
  "profitabilityScore": 21.0,
  "valuationScore": 21.0,
  "stabilityScore": 10.0,
  "dataQualityScore": 10.0,
  "dataQuality": "OK",
  "highlights": [
    "Revenue grew 40.6% year-over-year",
    "Net profit grew 168.9% year-over-year",
    "Net margin is healthy at 17.0%",
    "Positive profit in last 4 consecutive quarters"
  ],
  "warnings": [
    "Cash flow data is not available in Phase 1"
  ]
}
```

---

### `GET /internal/fa/tickers/{ticker}/financials`

Dữ liệu tài chính theo chuỗi thời gian.

**Query params**:

| Param | Default | Description |
|---|---|---|
| `periodType` | `QUARTER` | `QUARTER` / `YEAR` / `POINT_IN_TIME` |
| `batchId` | (latest SUCCESS) | |

**Response `200`**:

```json
{
  "ticker": "HPG",
  "periodType": "QUARTER",
  "batchId": 1001,
  "series": [
    {
      "metricCode": "REVENUE",
      "unit": "VND_BILLION",
      "values": [
        { "period": "2024Q1", "value": 32000.50, "quality": "OK" },
        { "period": "2024Q2", "value": 35000.00, "quality": "OK" },
        { "period": "2025Q1", "value": 38000.20, "quality": "OK" },
        { "period": "2026Q1", "value": 51000.80, "quality": "OK" }
      ]
    },
    {
      "metricCode": "NPAT",
      "unit": "VND_BILLION",
      "values": [...]
    }
  ]
}
```

---

### `GET /internal/fa/tickers/{ticker}/ratios`

Các tỷ số tài chính đã tính cho một kỳ.

**Query params**:

| Param | Default | Description |
|---|---|---|
| `period` | `2026Q1` | |
| `batchId` | (latest SUCCESS) | |

**Response `200`**:

```json
{
  "ticker": "HPG",
  "period": "2026Q1",
  "batchId": 1001,
  "ratios": [
    { "ratioCode": "REVENUE_YOY", "value": 40.60, "quality": "OK" },
    { "ratioCode": "NPAT_YOY", "value": 168.90, "quality": "OK" },
    { "ratioCode": "GROSS_MARGIN", "value": 21.5, "quality": "OK" },
    { "ratioCode": "NET_MARGIN", "value": 17.0, "quality": "OK" },
    { "ratioCode": "PE_TTM", "value": 9.82, "quality": "OK" },
    { "ratioCode": "PB", "value": 1.54, "quality": "OK" },
    { "ratioCode": "MARKET_CAP", "value": 207238000000000, "quality": "OK" },
    { "ratioCode": "POSITIVE_NPAT_LAST_4Q", "value": 1.0, "quality": "OK" }
  ]
}
```

---

### `GET /internal/fa/tickers/{ticker}/score-history`

Lịch sử FA Score qua các kỳ.

**Response `200`**:

```json
{
  "ticker": "HPG",
  "history": [
    {
      "period": "2024Q1",
      "overallScore": 75.5,
      "growthScore": 20.0,
      "profitabilityScore": 18.5,
      "valuationScore": 22.0,
      "stabilityScore": 10.0,
      "dataQualityScore": 5.0,
      "rating": "GOOD_FA",
      "batchId": 1001,
      "calculatedAt": "2026-06-30T10:03:00"
    },
    {
      "period": "2026Q1",
      "overallScore": 92.0,
      ...
    }
  ]
}
```

---

## 3. Screener API

### `GET /internal/fa/screener`

Lọc và xếp hạng cổ phiếu theo tiêu chí FA.

**Query params**:

| Param | Default | Description |
|---|---|---|
| `period` | `2026Q1` | Kỳ báo cáo |
| `rating` | (all) | `STRONG_FA` / `GOOD_FA` / `FAIR_FA` / `WEAK_FA` / `POOR_FA` |
| `minFaScore` | `0` | Điểm FA tối thiểu (0-100) |
| `exchange` | (all) | `HOSE` / `HNX` / `UPCOM` |
| `batchId` | (latest SUCCESS) | |
| `page` | `0` | 0-indexed |
| `pageSize` | `20` | Tối đa 100 |

**Response `200`**:

```json
{
  "page": 0,
  "pageSize": 20,
  "totalCount": 1630,
  "items": [
    {
      "ticker": "PVP",
      "companyName": "Vận tải Dầu khí Thái Bình Dương",
      "exchange": "HOSE",
      "industry": "Dịch vụ vận tải",
      "rating": "STRONG_FA",
      "overallScore": 90.0,
      "growthScore": 30.0,
      "profitabilityScore": 15.0,
      "valuationScore": 25.0,
      "dataQuality": "OK"
    }
  ]
}
```

> **Lưu ý**: `totalCount` là tổng số ticker thoả điều kiện filter. Số trang = `ceil(totalCount / pageSize)`.

---

## 4. Benchmark API

### `GET /internal/fa/benchmark`

Xem trung vị các chỉ số theo ngành (Industry Benchmark).

**Query params**:

| Param | Default | Description |
|---|---|---|
| `period` | `2026Q1` | |
| `batchId` | (latest SUCCESS) | |

**Response `200`**:

```json
{
  "period": "2026Q1",
  "benchmarks": [
    {
      "industry": "Thép và sản phẩm thép",
      "tickerCount": 12,
      "medianFaScore": 72.5,
      "medianGrossMargin": 18.5,
      "medianNetMargin": 8.2,
      "medianRevenueYoy": 12.0,
      "medianPe": 11.5,
      "medianPb": 1.4
    }
  ]
}
```

---

## Error Handling

| HTTP Status | Tình huống |
|---|---|
| `200 OK` | Thành công |
| `400 Bad Request` | Sai input (file lỗi, tham số invalid) |
| `404 Not Found` | Batch ID hoặc ticker không tồn tại |
| `500 Internal Server Error` | Lỗi server không mong đợi |

> Phase 1 chưa có chuẩn Error Response body. Phase 2 nên chuẩn hóa theo:
> ```json
> { "errorCode": "BATCH_NOT_FOUND", "message": "...", "timestamp": "..." }
> ```
