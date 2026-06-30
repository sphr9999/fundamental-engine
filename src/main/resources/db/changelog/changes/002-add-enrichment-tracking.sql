--liquibase formatted sql

--changeset fundamental-engine:002-add-enrichment-tracking
--comment: Add indexes and column to support VCI API enrichment data

-- Optimize query for enrichment: lookup by ticker + metric + batch
CREATE INDEX IF NOT EXISTS idx_fa_metric_ticker_metric_batch 
    ON fa_financial_metric(ticker, metric_code, import_batch_id);

-- Filter batches by source type (EXCEL_UPLOAD vs VCI_API)
CREATE INDEX IF NOT EXISTS idx_fa_import_batch_source_type 
    ON fa_import_batch(source_type);

-- Add column for enrichment progress tracking (JSON text)
ALTER TABLE fa_import_batch ADD COLUMN IF NOT EXISTS 
    enrichment_progress_json TEXT;
