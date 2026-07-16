-- V12: Fix task_assets constraints to match V3 spec.
-- task_assets uses asset_kind / asset_role / confirmed in V3, OpenAPI, and Java Entity.

-- Step 1: Migrate existing legacy role values before changing the constraint.
UPDATE task_assets SET asset_role = 'product_front' WHERE asset_role = 'product';
UPDATE task_assets SET asset_role = 'ai_keyframe' WHERE asset_role = 'keyframe';
UPDATE task_assets SET asset_role = 'scene_reference' WHERE asset_role = 'reference';
-- 'video_clip' stays the same.

-- Step 2: Replace role constraint with the full V3 role set.
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

-- Step 3: Replace kind/type constraints. V3 allows image/video/audio only.
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_kind;
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_type;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_kind CHECK (
    asset_kind IN ('image', 'video', 'audio')
);

-- Step 4: Replace source constraint.
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_source;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_source CHECK (
    source IN ('user_upload', 'ai_generated', 'external_url')
);
