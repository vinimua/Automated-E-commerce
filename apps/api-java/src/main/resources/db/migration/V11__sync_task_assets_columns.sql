-- V11: Add missing columns to task_assets table
ALTER TABLE task_assets
    ADD COLUMN IF NOT EXISTS source VARCHAR(80) NOT NULL DEFAULT 'user_upload',
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
