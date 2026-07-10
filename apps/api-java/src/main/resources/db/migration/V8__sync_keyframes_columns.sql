-- V8: Add missing columns to keyframes table
ALTER TABLE keyframes
    ADD COLUMN IF NOT EXISTS storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS asset_id UUID REFERENCES task_assets(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS material_id UUID REFERENCES materials(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS image_purpose VARCHAR(80) NOT NULL DEFAULT 'first_frame',
    ADD COLUMN IF NOT EXISTS user_instruction TEXT;

CREATE INDEX IF NOT EXISTS idx_keyframes_storyboard_id ON keyframes(storyboard_id);
