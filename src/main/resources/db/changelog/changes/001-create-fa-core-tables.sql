--liquibase formatted sql

--changeset fundamental-engine:001-create-fa-core-tables

-- ============================================================
-- dim_company: Company identity and sector metadata
-- ============================================================
CREATE TABLE IF NOT EXISTS dim_company (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    company_name TEXT,
    exchange VARCHAR(20),
    industry_level_1 TEXT,
    industry_level_2 TEXT,
    industry_level_3 TEXT,
    sector_model VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dim_company_exchange ON dim_company(exchange);
CREATE INDEX IF NOT EXISTS idx_dim_company_sector_model ON dim_company(sector_model);

-- ============================================================
-- fa_import_batch: Tracks each Excel import
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_import_batch (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_file_name TEXT NOT NULL,
    source_file_checksum VARCHAR(128) NOT NULL,
    report_period VARCHAR(20),
    status VARCHAR(30) NOT NULL,
    total_rows INTEGER DEFAULT 0,
    success_rows INTEGER DEFAULT 0,
    warning_rows INTEGER DEFAULT 0,
    error_rows INTEGER DEFAULT 0,
    imported_by VARCHAR(100),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fa_import_batch_status ON fa_import_batch(status);
CREATE INDEX IF NOT EXISTS idx_fa_import_batch_checksum ON fa_import_batch(source_file_checksum);
CREATE INDEX IF NOT EXISTS idx_fa_import_batch_report_period ON fa_import_batch(report_period);

-- ============================================================
-- fa_raw_cell: Raw cell-level evidence for traceability
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_raw_cell (
    id BIGSERIAL PRIMARY KEY,
    import_batch_id BIGINT NOT NULL REFERENCES fa_import_batch(id),
    sheet_name VARCHAR(200) NOT NULL,
    cell_ref VARCHAR(30) NOT NULL,
    row_index INTEGER NOT NULL,
    col_index INTEGER NOT NULL,
    raw_text TEXT,
    numeric_value NUMERIC(30, 6),
    formula_text TEXT,
    cell_type VARCHAR(30),
    error_code VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fa_raw_cell_batch_sheet ON fa_raw_cell(import_batch_id, sheet_name);
CREATE INDEX IF NOT EXISTS idx_fa_raw_cell_ref ON fa_raw_cell(import_batch_id, sheet_name, cell_ref);

-- ============================================================
-- fa_financial_metric: Main fact table for normalized financial metrics
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_financial_metric (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    period_type VARCHAR(30) NOT NULL,
    period_code VARCHAR(20) NOT NULL,
    metric_code VARCHAR(50) NOT NULL,
    metric_value NUMERIC(30, 6),
    unit VARCHAR(30),
    currency VARCHAR(10),
    quality_status VARCHAR(30) NOT NULL DEFAULT 'OK',
    quality_note TEXT,
    source_sheet VARCHAR(200),
    source_cell VARCHAR(30),
    import_batch_id BIGINT NOT NULL REFERENCES fa_import_batch(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(ticker, period_type, period_code, metric_code, import_batch_id)
);

CREATE INDEX IF NOT EXISTS idx_fa_metric_ticker_period ON fa_financial_metric(ticker, period_code);
CREATE INDEX IF NOT EXISTS idx_fa_metric_metric_period ON fa_financial_metric(metric_code, period_code);
CREATE INDEX IF NOT EXISTS idx_fa_metric_batch ON fa_financial_metric(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_fa_metric_quality ON fa_financial_metric(quality_status);

-- ============================================================
-- fa_financial_ratio: Calculated ratios
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_financial_ratio (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    period_code VARCHAR(20) NOT NULL,
    ratio_code VARCHAR(50) NOT NULL,
    ratio_value NUMERIC(30, 8),
    quality_status VARCHAR(30) NOT NULL DEFAULT 'OK',
    quality_note TEXT,
    calculation_version VARCHAR(50) NOT NULL,
    import_batch_id BIGINT NOT NULL REFERENCES fa_import_batch(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(ticker, period_code, ratio_code, calculation_version, import_batch_id)
);

CREATE INDEX IF NOT EXISTS idx_fa_ratio_ticker_period ON fa_financial_ratio(ticker, period_code);
CREATE INDEX IF NOT EXISTS idx_fa_ratio_code_period ON fa_financial_ratio(ratio_code, period_code);
CREATE INDEX IF NOT EXISTS idx_fa_ratio_batch ON fa_financial_ratio(import_batch_id);

-- ============================================================
-- fa_score_snapshot: Rule-based FA scores
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_score_snapshot (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    period_code VARCHAR(20) NOT NULL,
    growth_score NUMERIC(10, 2),
    profitability_score NUMERIC(10, 2),
    valuation_score NUMERIC(10, 2),
    stability_score NUMERIC(10, 2),
    data_quality_score NUMERIC(10, 2),
    overall_score NUMERIC(10, 2),
    rating VARCHAR(30),
    explanation TEXT,
    calculation_version VARCHAR(50) NOT NULL,
    import_batch_id BIGINT NOT NULL REFERENCES fa_import_batch(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(ticker, period_code, calculation_version, import_batch_id)
);

CREATE INDEX IF NOT EXISTS idx_fa_score_ticker_period ON fa_score_snapshot(ticker, period_code);
CREATE INDEX IF NOT EXISTS idx_fa_score_overall ON fa_score_snapshot(period_code, overall_score DESC);
CREATE INDEX IF NOT EXISTS idx_fa_score_rating ON fa_score_snapshot(period_code, rating);

-- ============================================================
-- fa_data_quality_issue: Quality issues found during import
-- ============================================================
CREATE TABLE IF NOT EXISTS fa_data_quality_issue (
    id BIGSERIAL PRIMARY KEY,
    import_batch_id BIGINT NOT NULL REFERENCES fa_import_batch(id),
    ticker VARCHAR(20),
    period_code VARCHAR(20),
    metric_code VARCHAR(50),
    issue_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_sheet VARCHAR(200),
    source_cell VARCHAR(30),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fa_quality_batch ON fa_data_quality_issue(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_fa_quality_ticker ON fa_data_quality_issue(ticker);
CREATE INDEX IF NOT EXISTS idx_fa_quality_severity ON fa_data_quality_issue(severity);
