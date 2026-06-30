# Roadmap Phase 2 — Data Enrichment

## Mục tiêu

Mở rộng từ "Income Statement (partial)" lên "Bộ BCTC đầy đủ" để phân tích chuyên sâu hơn.

```
Phase 1 ✅:  Income Statement (partial) + Market Data
Phase 2A 🎯: Balance Sheet + Solvency Ratios
Phase 2B 🎯: Cash Flow Statement + FCF
Phase 2C 🎯: Dividend History + Dividend Ratios
Phase 2D 🎯: Industry Benchmark nâng cao
Phase 3  🔮: Sector-specific models (Bank, Securities, Insurance)
```

---

## Phase 2A — Balance Sheet (Ưu tiên cao nhất)

### Lý do ưu tiên

Không có Balance Sheet = không tính được:
- **ROE** (Return on Equity) — chỉ số quan trọng nhất đánh giá hiệu quả vốn
- **D/E** (Debt-to-Equity) — đòn bẩy tài chính
- **Current Ratio** — thanh khoản ngắn hạn
- **Net Debt** — nợ ròng thực tế

### MetricCode cần thêm

```java
// Tài sản
TOTAL_ASSETS, CURRENT_ASSETS, CASH_AND_EQUIVALENTS,
SHORT_TERM_INVESTMENTS, ACCOUNTS_RECEIVABLE, INVENTORY,
NON_CURRENT_ASSETS, FIXED_ASSETS_NET, LONG_TERM_INVESTMENTS,

// Nợ
TOTAL_LIABILITIES, CURRENT_LIABILITIES, SHORT_TERM_DEBT,
ACCOUNTS_PAYABLE, NON_CURRENT_LIABILITIES, LONG_TERM_DEBT, TOTAL_DEBT,

// Vốn chủ sở hữu
TOTAL_EQUITY, CHARTER_CAPITAL, RETAINED_EARNINGS,
BOOK_VALUE_PER_SHARE,
```

### RatioCode cần thêm

```java
ROE, ROA, ROIC,
DEBT_TO_EQUITY, NET_DEBT, NET_DEBT_TO_EBITDA,
CURRENT_RATIO, QUICK_RATIO, INTEREST_COVERAGE,
ASSET_TURNOVER, INVENTORY_DAYS, RECEIVABLE_DAYS
```

### Workload ước tính

| Task | Effort |
|---|---|
| Mở rộng `MetricCode` enum | 0.5h |
| Mở rộng `RatioCode` enum | 0.5h |
| Thêm `BalanceSheetParser` (Excel infra) | 4-8h |
| Cập nhật `RatioCalculationService` | 3-4h |
| Cập nhật FA Score (thêm Solvency sub-score) | 2h |
| Cập nhật UI Charts (thêm BS chart) | 2h |
| **Tổng** | **~2-3 ngày** |

> **Prerequisite**: File Excel nguồn phải có sheet Balance Sheet. Nếu không có → cần nguồn dữ liệu thay thế (API crawl).

---

## Phase 2B — Cash Flow Statement

### Lý do quan trọng

Cash Flow giúp phân biệt "lợi nhuận kế toán" vs "tiền thật". Công ty có thể báo lãi nhưng CFO âm (danger sign).

### MetricCode cần thêm

```java
CFO,                    // Operating Cash Flow
CFI,                    // Investing Cash Flow
CFF,                    // Financing Cash Flow
CAPEX,                  // Capital Expenditure
FREE_CASH_FLOW,         // FCF = CFO - CapEx
DEPRECIATION_AMORTIZATION,
```

### RatioCode cần thêm

```java
EBITDA_MARGIN, OPERATING_MARGIN,
CFO_TO_NET_INCOME,      // Chất lượng lợi nhuận
FREE_CASH_FLOW_YIELD,   // FCF / Market Cap
PRICE_TO_CFO,
```

---

## Phase 2C — Dividend History

### Yêu cầu DB mới

```sql
CREATE TABLE fa_dividend_history (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(20) NOT NULL,
    ex_date         DATE NOT NULL,
    record_date     DATE,
    payment_date    DATE,
    dividend_type   VARCHAR(30) NOT NULL,   -- CASH | STOCK | BOND
    amount          NUMERIC(20, 4),          -- VND/cổ phần
    stock_ratio     NUMERIC(10, 6),          -- tỷ lệ cổ tức cổ phiếu
    fiscal_year     INTEGER,
    source          VARCHAR(50),             -- MANUAL | HNX | HOSE | CRAWL
    import_batch_id BIGINT REFERENCES fa_import_batch(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fa_dividend_ticker_date ON fa_dividend_history(ticker, ex_date DESC);
CREATE INDEX idx_fa_dividend_year ON fa_dividend_history(ticker, fiscal_year DESC);
```

### RatioCode cần thêm

```java
DIVIDEND_PER_SHARE,         // DPS TTM
DIVIDEND_YIELD,             // DPS / Price
DIVIDEND_PAYOUT_RATIO,      // DPS / EPS
DIVIDEND_CONSISTENCY_3Y,    // Boolean: trả cổ tức ≥ 3 năm liên tục
DIVIDEND_CONSISTENCY_5Y,    // Boolean: trả cổ tức ≥ 5 năm liên tục
DIVIDEND_GROWTH_3Y,         // CAGR cổ tức 3 năm
```

---

## Phase 2D — Industry Benchmark nâng cao

### Yêu cầu DB mới

```sql
CREATE TABLE fa_industry_benchmark (
    id               BIGSERIAL PRIMARY KEY,
    industry_code    VARCHAR(100) NOT NULL,
    period_code      VARCHAR(20) NOT NULL,
    metric_code      VARCHAR(50) NOT NULL,
    median_value     NUMERIC(30,8),
    p25_value        NUMERIC(30,8),
    p75_value        NUMERIC(30,8),
    sample_count     INTEGER,
    import_batch_id  BIGINT REFERENCES fa_import_batch(id),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(industry_code, period_code, metric_code, import_batch_id)
);
```

### Tính năng mới

- So sánh ticker vs trung vị ngành (ví dụ: MWG vs Bán lẻ)
- Chart "Định vị trong ngành" (percentile bar)
- Score tương đối (relative scoring) thay vì ngưỡng cứng

---

## Phase 3 — Sector-Specific Models

### Ngân hàng

Mô hình riêng với chỉ số:
```java
NIM,           // Net Interest Margin
NPL_RATIO,     // Tỷ lệ nợ xấu
CAR,           // Capital Adequacy Ratio
LOAN_TO_DEPOSIT_RATIO,
COST_OF_FUND,
```

### Chứng khoán & Bảo hiểm

Tương tự — sẽ spec chi tiết khi vào Phase 3.

---

## Nguồn dữ liệu Phase 2 (Cào dữ liệu)

| Nguồn | Dữ liệu | Ghi chú |
|---|---|---|
| **VietStock** | BCTC đầy đủ (BS, CF, P&L) | Cần API key hoặc scrape |
| **CafeF** | BCTC, cổ tức, tin tức | HTML scrape |
| **Fireant** | BCTC, tỷ số | API có tài liệu |
| **HNX/HOSE official** | Cổ tức chính thức | XML feed |
| **FiinTrade** | Data premium, ICB ngành | Trả phí |
| **Excel thủ công** | Tiếp tục từ file hiện tại | Phụ thuộc người cung cấp |

### Kiến trúc cào dữ liệu đề xuất

```
Crawler Service (Python/Go)
  → Fetch từ nguồn
  → Transform → JSON
  → POST /internal/fa/import-batches/json  (API mới Phase 2)
  → fundamental-engine lưu vào DB
```

> Không mix crawler logic vào fundamental-engine. Tách thành service riêng.

---

## FA Score Revision (Phase 2A)

Sau khi có Balance Sheet, điểm nên phân bổ lại:

| Hạng mục | Phase 1 | Phase 2A |
|---|---|---|
| Tăng trưởng | 30đ | 20đ |
| Khả năng sinh lời | 25đ | 20đ |
| Định giá | 25đ | 20đ |
| **Solvency (mới)** | — | **15đ** |
| **Cash Flow (mới)** | — | **10đ** |
| Ổn định | 10đ | 5đ |
| Chất lượng | 10đ | 5đ |
| **Cổ tức (Phase 2C)** | — | **5đ** |
| **Tổng** | **100** | **100** |
