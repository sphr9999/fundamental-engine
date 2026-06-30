#!/bin/bash
# ============================================================
# Data Duplication Analysis Script
# Kiểm tra trùng lặp giữa các batch TRƯỚC KHI xóa
# ============================================================

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-fundamental-engine}"
DB_USER="${DB_USER:-admin}"
export PGPASSWORD="${DB_PASSWORD:-123456}"

PSQL="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME --no-align --tuples-only"
PSQL_TABLE="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        DATA DUPLICATION ANALYSIS — fundamental-engine       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Database: $DB_NAME @ $DB_HOST:$DB_PORT"
echo "Time:     $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# ── 1. BATCH OVERVIEW ────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. IMPORT BATCH OVERVIEW"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$PSQL_TABLE -c "
SELECT id, source_type, status, total_rows, success_rows, 
       created_at::date as imported_date,
       source_file_name
FROM fa_import_batch ORDER BY id;
"

# ── 2. ROWS PER BATCH PER TABLE ─────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2. ROW COUNT PER BATCH (all tables)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$PSQL_TABLE -c "
SELECT 
  b.id as batch_id,
  b.source_type,
  COALESCE(m.metric_cnt, 0) as metrics,
  COALESCE(r.ratio_cnt, 0) as ratios,
  COALESCE(s.score_cnt, 0) as scores,
  COALESCE(q.quality_cnt, 0) as quality_issues
FROM fa_import_batch b
LEFT JOIN (SELECT import_batch_id, count(*) as metric_cnt FROM fa_financial_metric GROUP BY import_batch_id) m ON m.import_batch_id = b.id
LEFT JOIN (SELECT import_batch_id, count(*) as ratio_cnt FROM fa_financial_ratio GROUP BY import_batch_id) r ON r.import_batch_id = b.id
LEFT JOIN (SELECT import_batch_id, count(*) as score_cnt FROM fa_score_snapshot GROUP BY import_batch_id) s ON s.import_batch_id = b.id
LEFT JOIN (SELECT import_batch_id, count(*) as quality_cnt FROM fa_data_quality_issue GROUP BY import_batch_id) q ON q.import_batch_id = b.id
ORDER BY b.id;
"

# ── 3. METRIC OVERLAP: EXCEL BATCHES (3, 4, 7, 10) ──────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3. METRIC OVERLAP: EXCEL BATCHES (3 vs 4 vs 7 vs 10)"
echo "   Key = ticker + period_code + metric_code"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
echo "3a. Key overlap (same ticker+period+metric across batches):"
$PSQL_TABLE -c "
WITH batch_keys AS (
  SELECT import_batch_id, ticker, period_code, metric_code
  FROM fa_financial_metric
  WHERE import_batch_id IN (3, 4, 7, 10)
)
SELECT 
  a.import_batch_id as batch_a,
  b.import_batch_id as batch_b,
  count(*) as overlapping_keys,
  (SELECT count(*) FROM fa_financial_metric WHERE import_batch_id = a.import_batch_id) as batch_a_total,
  (SELECT count(*) FROM fa_financial_metric WHERE import_batch_id = b.import_batch_id) as batch_b_total,
  round(count(*)::numeric / NULLIF((SELECT count(*) FROM fa_financial_metric WHERE import_batch_id = a.import_batch_id), 0) * 100, 1) as overlap_pct
FROM batch_keys a
JOIN batch_keys b ON a.ticker = b.ticker 
  AND a.period_code = b.period_code 
  AND a.metric_code = b.metric_code
  AND a.import_batch_id < b.import_batch_id
GROUP BY a.import_batch_id, b.import_batch_id
ORDER BY a.import_batch_id, b.import_batch_id;
"

echo ""
echo "3b. Value match (same key AND same value = truly identical):"
$PSQL_TABLE -c "
SELECT 
  a.import_batch_id as batch_a,
  b.import_batch_id as batch_b,
  count(*) as same_key_count,
  sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as same_value_count,
  sum(CASE WHEN a.metric_value IS DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as diff_value_count,
  round(sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END)::numeric / NULLIF(count(*), 0) * 100, 1) as value_match_pct
FROM fa_financial_metric a
JOIN fa_financial_metric b ON a.ticker = b.ticker 
  AND a.period_code = b.period_code 
  AND a.metric_code = b.metric_code
  AND a.import_batch_id < b.import_batch_id
WHERE a.import_batch_id IN (3, 4, 7, 10) AND b.import_batch_id IN (3, 4, 7, 10)
GROUP BY a.import_batch_id, b.import_batch_id
ORDER BY a.import_batch_id, b.import_batch_id;
"

echo ""
echo "3c. Unique data in Batch 10 NOT in Batch 3 (extra rows):"
$PSQL_TABLE -c "
SELECT count(*) as extra_rows_in_batch10,
  count(DISTINCT ticker) as extra_tickers,
  count(DISTINCT metric_code) as extra_metrics
FROM fa_financial_metric a
WHERE a.import_batch_id = 10
AND NOT EXISTS (
  SELECT 1 FROM fa_financial_metric b
  WHERE b.import_batch_id = 3
  AND b.ticker = a.ticker AND b.period_code = a.period_code AND b.metric_code = a.metric_code
);
"

# ── 4. METRIC OVERLAP: VCI BATCHES (13, 14, 15) ─────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4. METRIC OVERLAP: VCI BATCHES (13 vs 14 vs 15)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
echo "4a. Key + Value match:"
$PSQL_TABLE -c "
SELECT 
  a.import_batch_id as batch_a,
  b.import_batch_id as batch_b,
  count(*) as same_key_count,
  sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as same_value,
  sum(CASE WHEN a.metric_value IS DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as diff_value,
  round(sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END)::numeric / NULLIF(count(*), 0) * 100, 1) as value_match_pct
FROM fa_financial_metric a
JOIN fa_financial_metric b ON a.ticker = b.ticker 
  AND a.period_code = b.period_code 
  AND a.metric_code = b.metric_code
  AND a.import_batch_id < b.import_batch_id
WHERE a.import_batch_id IN (13, 14, 15) AND b.import_batch_id IN (13, 14, 15)
GROUP BY a.import_batch_id, b.import_batch_id
ORDER BY a.import_batch_id, b.import_batch_id;
"

echo ""
echo "4b. Batch 13 (1-ticker test) — is it a subset of 14/15?"
$PSQL_TABLE -c "
SELECT 
  (SELECT count(*) FROM fa_financial_metric WHERE import_batch_id = 13) as batch13_rows,
  count(*) as found_in_batch15
FROM fa_financial_metric a
WHERE a.import_batch_id = 13
AND EXISTS (
  SELECT 1 FROM fa_financial_metric b
  WHERE b.import_batch_id = 15
  AND b.ticker = a.ticker AND b.period_code = a.period_code AND b.metric_code = a.metric_code
);
"

# ── 5. EXCEL vs VCI OVERLAP ─────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5. EXCEL (Batch 10) vs VCI (Batch 15) — do they overlap?"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$PSQL_TABLE -c "
SELECT 
  count(*) as overlapping_keys,
  sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as same_value,
  sum(CASE WHEN a.metric_value IS DISTINCT FROM b.metric_value THEN 1 ELSE 0 END) as diff_value,
  round(sum(CASE WHEN a.metric_value IS NOT DISTINCT FROM b.metric_value THEN 1 ELSE 0 END)::numeric / NULLIF(count(*), 0) * 100, 1) as value_match_pct
FROM fa_financial_metric a
JOIN fa_financial_metric b ON a.ticker = b.ticker 
  AND a.period_code = b.period_code 
  AND a.metric_code = b.metric_code
WHERE a.import_batch_id = 10 AND b.import_batch_id = 15;
"

echo ""
echo "5b. Metrics ONLY in Excel (Batch 10), NOT in VCI (Batch 15):"
$PSQL_TABLE -c "
SELECT metric_code, count(*) as rows
FROM fa_financial_metric a
WHERE a.import_batch_id = 10
AND NOT EXISTS (
  SELECT 1 FROM fa_financial_metric b
  WHERE b.import_batch_id = 15
  AND b.ticker = a.ticker AND b.period_code = a.period_code AND b.metric_code = a.metric_code
)
GROUP BY metric_code ORDER BY count(*) DESC;
"

echo ""
echo "5c. Metrics ONLY in VCI (Batch 15), NOT in Excel (Batch 10):"
$PSQL_TABLE -c "
SELECT metric_code, count(*) as rows
FROM fa_financial_metric a
WHERE a.import_batch_id = 15
AND NOT EXISTS (
  SELECT 1 FROM fa_financial_metric b
  WHERE b.import_batch_id = 10
  AND b.ticker = a.ticker AND b.period_code = a.period_code AND b.metric_code = a.metric_code
)
GROUP BY metric_code ORDER BY count(*) DESC
LIMIT 20;
"

# ── 6. RATIO & SCORE DUPLICATION ─────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "6. RATIO & SCORE DUPLICATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$PSQL_TABLE -c "
SELECT 'fa_financial_ratio' as table_name, import_batch_id, count(*) as rows
FROM fa_financial_ratio GROUP BY import_batch_id
UNION ALL
SELECT 'fa_score_snapshot', import_batch_id, count(*)
FROM fa_score_snapshot GROUP BY import_batch_id
UNION ALL
SELECT 'fa_data_quality_issue', import_batch_id, count(*)
FROM fa_data_quality_issue GROUP BY import_batch_id
ORDER BY table_name, import_batch_id;
"

# ── 7. SIZE ESTIMATE ─────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "7. SIZE ESTIMATE — if we keep only Batch 10 + 15"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$PSQL_TABLE -c "
SELECT 
  'KEEP (Batch 10+15)' as plan,
  (SELECT count(*) FROM fa_financial_metric WHERE import_batch_id IN (10,15)) as metric_rows,
  (SELECT count(*) FROM fa_financial_ratio WHERE import_batch_id IN (10,15)) as ratio_rows,
  (SELECT count(*) FROM fa_score_snapshot WHERE import_batch_id IN (10,15)) as score_rows
UNION ALL
SELECT 
  'DELETE (rest)',
  (SELECT count(*) FROM fa_financial_metric WHERE import_batch_id NOT IN (10,15)),
  (SELECT count(*) FROM fa_financial_ratio WHERE import_batch_id NOT IN (10,15)),
  (SELECT count(*) FROM fa_score_snapshot WHERE import_batch_id NOT IN (10,15));
"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Analysis complete. Review results above before deleting."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
