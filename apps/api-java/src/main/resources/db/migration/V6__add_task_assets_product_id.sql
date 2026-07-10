-- V6: Add product_id column to task_assets (was missing from earlier migration)
ALTER TABLE task_assets
    ADD COLUMN IF NOT EXISTS product_id UUID REFERENCES products(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_task_assets_product_id ON task_assets(product_id);
