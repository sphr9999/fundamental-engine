# Feature: FA Score (Rule-Based Scoring)

## Mục đích

Chuyển đổi các chỉ số tài chính và tỷ số đã tính thành một điểm tổng hợp từ 0-100, giúp so sánh và xếp hạng cổ phiếu một cách khách quan.

## Khi nào tính điểm?

FA Score được tính **tự động** sau mỗi lần import Excel thành công:

```
ExcelImportOrchestrator
  → RatioCalculationService.calculateForBatch()   # Step 1: tính ratios
  → FaScoreCalculationService.calculateForBatch() # Step 2: tính scores từ ratios
```

## Cấu trúc điểm (Tổng: 100)

```
┌─────────────────────┬────────┬─────────────────────────────────────────────┐
│ Hạng mục            │ Max    │ Tiêu chí                                     │
├─────────────────────┼────────┼─────────────────────────────────────────────┤
│ Tăng trưởng         │ 30đ    │ Revenue YoY + NPAT YoY                       │
│ Khả năng sinh lời   │ 25đ    │ Net Margin + Gross Margin                    │
│ Định giá            │ 25đ    │ P/E TTM + P/B                                │
│ Ổn định             │ 10đ    │ NPAT dương liên tục 4 quý                   │
│ Chất lượng dữ liệu  │ 10đ    │ % metrics có quality = OK                   │
└─────────────────────┴────────┴─────────────────────────────────────────────┘
```

## Chi tiết từng hạng mục

### 1. Tăng trưởng (30đ)

#### Revenue YoY (15đ)
| Mức tăng trưởng | Điểm |
|---|---|
| > 20% | 15đ (tối đa) |
| > 10% | 10đ |
| > 0% | 5đ |
| ≤ 0% | 0đ |

#### NPAT YoY (15đ)
Cùng thang điểm như Revenue YoY.

### 2. Khả năng sinh lời (25đ)

#### Net Margin (15đ)
| Biên LN ròng | Điểm |
|---|---|
| > 15% | 15đ |
| > 8% | 9đ |
| > 0% | 3đ |
| ≤ 0% | 0đ |

#### Gross Margin (10đ)
| Biên LN gộp | Điểm |
|---|---|
| > 30% | 10đ |
| > 15% | 6đ |
| > 0% | 2đ |
| ≤ 0% | 0đ |

> **Lưu ý ngành**: Ngành Bán lẻ (MWG) và Phân phối có biên mỏng tự nhiên (Net Margin 1-3%), sẽ bị điểm thấp ở hạng mục này so với ngành Phần mềm, Ngân hàng. Đây là hạn chế của mô hình chung (GENERAL). Phase 2 sẽ có model riêng theo sector.

### 3. Định giá (25đ) — Điểm thấp = tốt

#### P/E TTM (15đ)
| P/E | Điểm |
|---|---|
| ≤ 0 (lỗ) | 0đ |
| < 10 | 15đ |
| < 15 | 12đ |
| < 20 | 8đ |
| < 30 | 4đ |
| ≥ 30 | 0đ |
| Không có data P/E | 7.5đ (neutral) |

#### P/B (10đ)
| P/B | Điểm |
|---|---|
| ≤ 0 | 0đ |
| < 1 | 10đ |
| < 1.5 | 8đ |
| < 2 | 6đ |
| < 3 | 3đ |
| ≥ 3 | 0đ |
| Không có data P/B | 5đ (neutral) |

### 4. Ổn định (10đ)

| Điều kiện | Điểm |
|---|---|
| NPAT dương cả 4 quý gần nhất | 10đ |
| Bất kỳ quý nào lỗ | 0đ |

### 5. Chất lượng dữ liệu (10đ)

```
dataQualityScore = 10 × (số metrics có quality=OK) / (tổng metrics)
```

Ví dụ: 8/10 metrics OK → 8.0 điểm.

---

## Rating Thresholds

| Điểm | Rating | Ý nghĩa |
|---|---|---|
| ≥ 80 | `STRONG_FA` | Cơ bản xuất sắc |
| ≥ 65 | `GOOD_FA` | Cơ bản tốt |
| ≥ 50 | `FAIR_FA` | Cơ bản đạt yêu cầu |
| ≥ 35 | `WEAK_FA` | Cơ bản yếu |
| < 35 | `POOR_FA` | Cơ bản tệ |

---

## Calculation Versioning

Field `calculation_version = "v1.0"` trong bảng `fa_score_snapshot` và `fa_financial_ratio`.

- Cho phép chạy lại tính toán với logic mới (v1.1, v2.0) mà không xóa snapshot cũ.
- API screener và overview luôn dùng version mới nhất nếu không specify.

---

## Hạn chế hiện tại (Phase 1)

1. **Mô hình chung** — không phân biệt sector. Ngành bán lẻ/phân phối bị điểm Sinh lời thấp mặc dù business tốt.
2. **Thiếu Balance Sheet** — không có ROE, D/E, Current Ratio trong scoring.
3. **Thiếu Cash Flow** — không phân biệt được "lợi nhuận sổ sách" vs "tiền thật".
4. **Thiếu Dividend** — không tính điểm cổ tức.
5. **Thang điểm cứng** — ngưỡng không tự điều chỉnh theo chu kỳ thị trường.
