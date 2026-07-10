-- V12: Fix task_assets constraints to match V3 spec
-- Current constraints only allow: keyframe, video_clip, reference, product
-- V3 spec requires 14 role values and proper type values

-- Step 1: Migrate existing data to valid V3 roles before changing constraint
-- 'product' → 'product_front', 'keyframe' → 'ai_keyframe', 'reference' → 'scene_reference'
UPDATE task_assets SET role = 'product_front' WHERE role = 'product';
UPDATE task_assets SET role = 'ai_keyframe' WHERE role = 'keyframe';
UPDATE task_assets SET role = 'scene_reference' WHERE role = 'reference';
-- 'video_clip' stays the same

-- Step 2: Replace role constraint with full V3 set
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_role;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_role CHECK (
    role IN (
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

-- Step 3: Replace type constraint (old allowed 'product_image', V3 only allows image/video/audio)
ALTER TABLE task_assets DROP CONSTRAINT IF EXISTS chk_task_assets_type;
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_type CHECK (
    type IN ('image', 'video', 'audio')
);

-- Step 4: Add source constraint (missing entirely)
ALTER TABLE task_assets ADD CONSTRAINT chk_task_assets_source CHECK (
    source IN ('user_upload', 'ai_generated', 'external_url')
);
