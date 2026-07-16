-- V13: Normalize task_assets columns for databases that were created with legacy names.
-- Canonical columns are asset_kind / asset_role / confirmed.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'type'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'asset_kind'
    ) THEN
        ALTER TABLE task_assets RENAME COLUMN type TO asset_kind;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'role'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'asset_role'
    ) THEN
        ALTER TABLE task_assets RENAME COLUMN role TO asset_role;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'is_confirmed'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'task_assets' AND column_name = 'confirmed'
    ) THEN
        ALTER TABLE task_assets RENAME COLUMN is_confirmed TO confirmed;
    END IF;
END $$;

UPDATE task_assets SET asset_role = 'product_front' WHERE asset_role = 'product';
UPDATE task_assets SET asset_role = 'ai_keyframe' WHERE asset_role = 'keyframe';
UPDATE task_assets SET asset_role = 'scene_reference' WHERE asset_role = 'reference';

ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_kind;
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_type;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_kind CHECK (
    asset_kind IN ('image', 'video', 'audio')
);

ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_role;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_role CHECK (
    asset_role IN (
        'product_front',
        'product_back',
        'product_detail',
        'model_reference',
        'scene_reference',
        'outfit_reference',
        'reference_video',
        'user_keyframe',
        'generated_result',
        'ai_keyframe',
        'image_variant',
        'video_clip',
        'final_video',
        'cover_image'
    )
);

ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_source;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_source CHECK (
    source IN ('user_upload', 'ai_generated', 'external_url')
);
