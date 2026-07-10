-- V10: Sync repair_events table with Entity expectations
-- Add missing jsonb columns
ALTER TABLE repair_events
    ADD COLUMN IF NOT EXISTS repair_scope JSONB,
    ADD COLUMN IF NOT EXISTS repair_plan JSONB;
