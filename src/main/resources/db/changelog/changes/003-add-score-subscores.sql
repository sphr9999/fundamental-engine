--liquibase formatted sql

--changeset fundamental-engine:003-add-score-subscores
--comment: Add solvency and cash flow sub-score columns for Phase 2A FA Score v2.0

ALTER TABLE fa_score_snapshot ADD COLUMN IF NOT EXISTS
    solvency_score numeric(10, 2);

ALTER TABLE fa_score_snapshot ADD COLUMN IF NOT EXISTS
    cashflow_score numeric(10, 2);
