# Feature: Ticker Dashboard

## Mục đích

Hiển thị toàn bộ thông tin phân tích cơ bản của một mã cổ phiếu: tổng quan, tài chính lịch sử, tỷ số, điểm FA.

## APIs sử dụng

Dashboard của một ticker gọi song song 4 APIs:

```javascript
Promise.all([
  GET /internal/fa/tickers/{ticker}/overview
  GET /internal/fa/tickers/{ticker}/ratios
  GET /internal/fa/tickers/{ticker}/score-history
  GET /internal/fa/tickers/{ticker}/financials?periodType=QUARTER
  GET /internal/fa/tickers/{ticker}/financials?periodType=POINT_IN_TIME
])
```

## Các thành phần giao diện

### 1. Header
- Ticker lớn (bold)
- Tên công ty
- Sàn, Ngành
- Link sang kỹ thuật (eybroker)

### 2. Macro Banner
- Bối cảnh vĩ mô Việt Nam (hardcoded Phase 1)

### 3. Metric Cards (4 cards)
| Card | Data source |
|---|---|
| Giá (VND) | `overview.price` |
| P/E (TTM) | `overview.peTtm` |
| P/B | `overview.pb` |
| Vốn hóa (Tỷ) | `overview.marketCap` |

### 4. Charts (4 biểu đồ)

| # | Tên | Type | Data source |
|---|---|---|---|
| 1 | Doanh thu & Lợi nhuận (Tỷ VND) | Bar chart | `financials.series[REVENUE, NPAT]` |
| 2 | Biên lợi nhuận (%) | Line chart | Tính client-side: GROSS_PROFIT/REVENUE, NPAT/REVENUE |
| 3 | Định giá (Giá & P/B) | Dual-axis line | `financials.series[CLOSE_PRICE, PB]` (POINT_IN_TIME) |
| 4 | Lịch sử FA Score | Line area chart | `score-history.history[overallScore]` |

### 5. Phân rã FA Score

Hiển thị thanh progress bar với 5 hạng mục:

| Hạng mục | Color | Max | Tooltip |
|---|---|---|---|
| Tăng trưởng | `#4caf50` | 30đ | "Tối đa 30đ. Dựa trên tăng trưởng Doanh thu & Lợi nhuận YoY" |
| Lợi nhuận | `#00bcd4` | 25đ | "Tối đa 25đ. Dựa trên Biên lợi nhuận Gộp & Ròng" |
| Định giá | `#ba68c8` | 25đ | "Tối đa 25đ. Dựa trên chỉ số P/E và P/B" |
| Ổn định | `#f5c518` | 10đ | "Tối đa 10đ. Đánh giá chuỗi lợi nhuận dương liên tục" |
| Chất lượng | `#ff9800` | 10đ | "Tối đa 10đ. Đánh giá độ tin cậy và đầy đủ của dữ liệu" |

Tooltip hiển thị khi hover vào icon **ⓘ** bên cạnh tên hạng mục.

### 6. Điểm mạnh & Lưu ý

- **Điểm mạnh** (`✅`): Auto-generate từ ratios (revenue_yoy > 20%, net_margin > 15%, v.v.)
- **Lưu ý** (`⚠️`): Cảnh báo thiếu data hoặc vấn đề chất lượng.

### 7. Bảng Ratios chi tiết

Hiển thị toàn bộ `ratios[]` từ API `/ratios` với cột: Ratio Code, Giá trị, Quality badge.

## Chart Library

Dùng **Chart.js** (CDN). Không có bundler/framework — SPA thuần HTML + Vanilla JS trong `index.html`.

## Lưu ý kỹ thuật

- Biên lợi nhuận tính **client-side** (không có API riêng):
  ```javascript
  grossMargin = (GROSS_PROFIT / REVENUE) * 100
  netMargin = (NPAT / REVENUE) * 100
  ```
- POINT_IN_TIME metrics (Giá, P/B) được gọi riêng qua `?periodType=POINT_IN_TIME` rồi merge vào `financials.series` ở frontend.
- Chart instance cũ phải `destroy()` trước khi render lại để tránh memory leak.
