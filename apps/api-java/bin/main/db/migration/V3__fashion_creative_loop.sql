-- ============================================================
-- Fashion Creative Loop V1
-- Database Migration
-- Flyway: V3__fashion_creative_loop.sql
-- ============================================================
-- This migration adds human-in-the-loop fashion creative workflow
-- contracts: task mode, asset roles, creative state, keyframes,
-- video clips, repair events, fingerprints, and QA results.
-- ============================================================

-- ============================================================
-- 1. Extend video_tasks table
-- ============================================================

ALTER TABLE video_tasks
    ADD COLUMN IF NOT EXISTS task_mode VARCHAR(80) NOT NULL DEFAULT 'PRODUCT_CREATIVE',
    ADD COLUMN IF NOT EXISTS product_category VARCHAR(80) NOT NULL DEFAULT 'general',
    ADD COLUMN IF NOT EXISTS shot_count INT,
    ADD COLUMN IF NOT EXISTS current_version INT NOT NULL DEFAULT 1;

ALTER TABLE video_tasks
    ADD CONSTRAINT chk_video_tasks_task_mode CHECK (
        task_mode IN (
            'PRODUCT_CREATIVE',
            'REFERENCE_STORYBOARD',
            'USER_SCRIPT',
            'CUSTOM_STORYBOARD'
        )
    ),
    ADD CONSTRAINT chk_video_tasks_shot_count CHECK (
        shot_count IS NULL OR shot_count > 0
    ),
    ADD CONSTRAINT chk_video_tasks_current_version CHECK (current_version >= 1);

ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS chk_video_tasks_status;

ALTER TABLE video_tasks ADD CONSTRAINT chk_video_tasks_status CHECK (
    status IN (
        'draft',
        'asset_uploading',
        'asset_analyzing',
        'waiting_asset_confirmation',
        'reference_analyzing',
        'plan_generating',
        'analyzing',
        'analysis_completed',
        'plan_generated',
        'waiting_plan_selection',
        'storyboard_generating',
        'script_generating',
        'script_generated',
        'material_generating',
        'material_generated',
        'rendering',
        'checking',
        'completed',
        'failed',
        'exported',
        'waiting_storyboard_confirmation',
        'keyframe_configuring',
        'image_generating',
        'waiting_image_confirmation',
        'video_clip_generating',
        'waiting_video_clip_confirmation',
        'waiting_final_review',
        'repairing',
        'cancelled'
    )
);

-- ============================================================
-- 2. Task Assets
-- ============================================================

CREATE TABLE IF NOT EXISTS task_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,

    asset_kind VARCHAR(50) NOT NULL,
    asset_role VARCHAR(80) NOT NULL,
    source VARCHAR(80) NOT NULL,

    url TEXT NOT NULL,
    file_name VARCHAR(255),
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    description TEXT,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_task_assets_kind CHECK (
        asset_kind IN ('image', 'video', 'audio')
    ),
    CONSTRAINT chk_task_assets_role CHECK (
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
    ),
    CONSTRAINT chk_task_assets_source CHECK (
        source IN ('user_upload', 'ai_generated', 'external_url')
    )
);

CREATE INDEX IF NOT EXISTS idx_task_assets_task_id ON task_assets(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assets_user_id ON task_assets(user_id);
CREATE INDEX IF NOT EXISTS idx_task_assets_product_id ON task_assets(product_id);
CREATE INDEX IF NOT EXISTS idx_task_assets_role ON task_assets(asset_role);

-- ============================================================
-- 3. Creative States
-- ============================================================

CREATE TABLE IF NOT EXISTS creative_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    product_json JSONB,
    model_json JSONB,
    scene_json JSONB,
    outfit_json JSONB,
    reference_video_json JSONB,
    constraints_json JSONB,
    user_requirements_json JSONB,

    version INT NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_creative_states_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_creative_states_task_id ON creative_states(task_id);
CREATE INDEX IF NOT EXISTS idx_creative_states_user_id ON creative_states(user_id);

-- ============================================================
-- 4. Keyframes
-- ============================================================

CREATE TABLE IF NOT EXISTS keyframes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE,
    shot_id UUID REFERENCES storyboard_shots(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    shot_no INT NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'ai_generated',
    asset_id UUID REFERENCES task_assets(id) ON DELETE SET NULL,
    material_id UUID REFERENCES materials(id) ON DELETE SET NULL,
    image_purpose VARCHAR(80) NOT NULL DEFAULT 'first_frame',
    url TEXT,
    prompt TEXT,
    negative_prompt TEXT,
    user_instruction TEXT,
    provider VARCHAR(100),
    model_name VARCHAR(100),

    status VARCHAR(50) NOT NULL DEFAULT 'draft',

    version INT NOT NULL DEFAULT 1,
    error_message TEXT,
    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_keyframes_source CHECK (
        source IN ('user_upload', 'existing_asset', 'ai_generated')
    ),
    CONSTRAINT chk_keyframes_image_purpose CHECK (
        image_purpose IN ('first_frame', 'last_frame', 'reference', 'product_detail')
    ),
    CONSTRAINT chk_keyframes_status CHECK (
        status IN ('draft', 'generating', 'generated', 'uploaded', 'confirmed', 'rejected', 'failed')
    ),
    CONSTRAINT chk_keyframes_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_keyframes_task_id ON keyframes(task_id);
CREATE INDEX IF NOT EXISTS idx_keyframes_storyboard_id ON keyframes(storyboard_id);
CREATE INDEX IF NOT EXISTS idx_keyframes_shot_id ON keyframes(shot_id);
CREATE INDEX IF NOT EXISTS idx_keyframes_user_id ON keyframes(user_id);
CREATE INDEX IF NOT EXISTS idx_keyframes_status ON keyframes(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_keyframes_task_shot_version
    ON keyframes(task_id, shot_no, version);

-- ============================================================
-- 5. Video Clips
-- ============================================================

CREATE TABLE IF NOT EXISTS video_clips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE,
    shot_id UUID REFERENCES storyboard_shots(id) ON DELETE CASCADE,
    keyframe_id UUID REFERENCES keyframes(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    shot_no INT NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'ai_generated',
    url TEXT,
    prompt TEXT,
    negative_prompt TEXT,
    provider VARCHAR(100),
    model_name VARCHAR(100),

    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    duration INT NOT NULL,

    version INT NOT NULL DEFAULT 1,
    error_message TEXT,
    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_video_clips_source CHECK (
        source IN ('user_upload', 'ai_generated')
    ),
    CONSTRAINT chk_video_clips_status CHECK (
        status IN ('draft', 'generating', 'generated', 'uploaded', 'confirmed', 'rejected', 'failed')
    ),
    CONSTRAINT chk_video_clips_version CHECK (version >= 1),
    CONSTRAINT chk_video_clips_duration CHECK (duration > 0)
);

CREATE INDEX IF NOT EXISTS idx_video_clips_task_id ON video_clips(task_id);
CREATE INDEX IF NOT EXISTS idx_video_clips_storyboard_id ON video_clips(storyboard_id);
CREATE INDEX IF NOT EXISTS idx_video_clips_shot_id ON video_clips(shot_id);
CREATE INDEX IF NOT EXISTS idx_video_clips_keyframe_id ON video_clips(keyframe_id);
CREATE INDEX IF NOT EXISTS idx_video_clips_user_id ON video_clips(user_id);
CREATE INDEX IF NOT EXISTS idx_video_clips_status ON video_clips(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_video_clips_task_shot_version
    ON video_clips(task_id, shot_no, version);

-- ============================================================
-- 6. Repair Events
-- ============================================================

CREATE TABLE IF NOT EXISTS repair_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(100),
    user_feedback TEXT NOT NULL,
    issue_type VARCHAR(100),
    repair_scope JSONB,
    repair_plan JSONB,
    before_version INT,
    after_version INT,
    status VARCHAR(50) NOT NULL DEFAULT 'created',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_repair_events_target_type CHECK (
        target_type IN ('storyboard', 'keyframe', 'video_clip', 'render_manifest', 'final_video', 'plan')
    ),
    CONSTRAINT chk_repair_events_status CHECK (
        status IN ('created', 'in_progress', 'completed', 'failed')
    )
);

CREATE INDEX IF NOT EXISTS idx_repair_events_task_id ON repair_events(task_id);
CREATE INDEX IF NOT EXISTS idx_repair_events_user_id ON repair_events(user_id);
CREATE INDEX IF NOT EXISTS idx_repair_events_status ON repair_events(status);

-- ============================================================
-- 7. Video Fingerprints
-- ============================================================

CREATE TABLE IF NOT EXISTS video_fingerprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    opening_type VARCHAR(120),
    main_action VARCHAR(120),
    ending_type VARCHAR(120),
    main_visual VARCHAR(120),
    shot_sequence JSONB,
    camera_sequence JSONB,
    scene_position JSONB,
    similarity_score INT,
    compared_with JSONB,
    raw_fingerprint JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_video_fingerprints_similarity_score CHECK (
        similarity_score IS NULL OR (similarity_score >= 0 AND similarity_score <= 100)
    )
);

CREATE INDEX IF NOT EXISTS idx_video_fingerprints_task_id ON video_fingerprints(task_id);
CREATE INDEX IF NOT EXISTS idx_video_fingerprints_product_id ON video_fingerprints(product_id);
CREATE INDEX IF NOT EXISTS idx_video_fingerprints_user_id ON video_fingerprints(user_id);

-- ============================================================
-- 8. QA Results
-- ============================================================

CREATE TABLE IF NOT EXISTS qa_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(100),
    score INT,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    issues JSONB,
    suggestions JSONB,
    repair_instruction TEXT,
    raw_ai_output JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_qa_results_score CHECK (
        score IS NULL OR (score >= 0 AND score <= 100)
    ),
    CONSTRAINT chk_qa_results_target_type CHECK (
        target_type IN ('storyboard', 'keyframe', 'video_clip', 'plan', 'final_video')
    )
);

CREATE INDEX IF NOT EXISTS idx_qa_results_task_id ON qa_results(task_id);
CREATE INDEX IF NOT EXISTS idx_qa_results_user_id ON qa_results(user_id);
CREATE INDEX IF NOT EXISTS idx_qa_results_target_type ON qa_results(target_type);
