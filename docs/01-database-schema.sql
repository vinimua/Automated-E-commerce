-- ============================================================
-- TikTok Shop AI Video System
-- Database Schema
-- PostgreSQL
-- Version: V1 Core + Future Extension Reserved
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. ENUM-LIKE CHECK VALUES
-- ============================================================

-- users.role: user / admin
-- users.status: active / disabled

-- video_tasks.status:
-- draft / asset_uploading / asset_analyzing / waiting_asset_confirmation /
-- reference_analyzing / plan_generating / analyzing / analysis_completed /
-- plan_generated / waiting_plan_selection / storyboard_generating /
-- script_generating / script_generated /
-- material_generating / material_generated / rendering / checking /
-- completed / failed / exported / cancelled
--
-- Fashion Creative Loop extension:
-- waiting_storyboard_confirmation / keyframe_configuring /
-- image_generating / waiting_image_confirmation /
-- video_clip_generating / waiting_video_clip_confirmation /
-- waiting_final_review / repairing
--
-- video_tasks.task_mode:
-- PRODUCT_CREATIVE / REFERENCE_STORYBOARD / USER_SCRIPT / CUSTOM_STORYBOARD

-- videos.status:
-- completed / exported / deleted

-- materials.status:
-- generating / completed / failed / replaced
--
-- keyframes.source:
-- user_upload / existing_asset / ai_generated
--
-- keyframes.status:
-- draft / generating / generated / uploaded / confirmed / rejected / failed
--
-- video_clips.source:
-- user_upload / ai_generated
--
-- video_clips.status:
-- draft / generating / generated / uploaded / confirmed / rejected / failed
--
-- repair_events.status:
-- created / in_progress / completed / failed
--
-- repair_events.target_type:
-- storyboard / keyframe / video_clip / render_manifest / final_video / plan

-- quota_records.direction:
-- consume / refund / grant

-- model_logs.status:
-- success / failed

-- render_logs.status:
-- success / failed

-- Contract versions:
-- schema_version / manifest_version use semantic versions such as 1.0.0.
-- V1 code must persist the exact contract version used by AI callbacks and
-- RenderManifest so old tasks remain debuggable after future schema changes.

-- video_tasks.status transition (V1 Core):
-- draft -> analyzing -> analysis_completed -> plan_generated -> waiting_plan_selection
-- waiting_plan_selection -> script_generating -> script_generated
-- script_generated -> material_generating -> material_generated
-- material_generated -> rendering -> checking -> completed
-- completed -> exported
-- Any in-progress state may transition to failed.
--
-- video_tasks.status transition (Fashion Creative Loop extension):
-- waiting_storyboard_confirmation -> keyframe_configuring
-- keyframe_configuring -> image_generating
-- keyframe_configuring -> waiting_image_confirmation
-- waiting_image_confirmation -> video_clip_generating
-- waiting_video_clip_confirmation -> rendering
-- waiting_final_review -> completed
-- waiting_final_review -> repairing

-- ============================================================
-- 2. USERS
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_users_role CHECK (role IN ('user', 'admin')),
    CONSTRAINT chk_users_status CHECK (status IN ('active', 'disabled'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- ============================================================
-- 3. USER QUOTAS
-- ============================================================

CREATE TABLE user_quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    video_quota INT NOT NULL DEFAULT 10,
    image_quota INT NOT NULL DEFAULT 50,
    video_clip_quota INT NOT NULL DEFAULT 10,
    export_quota INT NOT NULL DEFAULT 10,

    used_video_count INT NOT NULL DEFAULT 0,
    used_image_count INT NOT NULL DEFAULT 0,
    used_video_clip_count INT NOT NULL DEFAULT 0,
    used_export_count INT NOT NULL DEFAULT 0,

    quota_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_quotas_non_negative CHECK (
        video_quota >= 0
        AND image_quota >= 0
        AND video_clip_quota >= 0
        AND export_quota >= 0
        AND used_video_count >= 0
        AND used_image_count >= 0
        AND used_video_clip_count >= 0
        AND used_export_count >= 0
    )
);

CREATE UNIQUE INDEX uq_user_quotas_user_id ON user_quotas(user_id);

-- ============================================================
-- 4. PRODUCTS
-- ============================================================

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,
    description TEXT,
    product_link TEXT,

    category VARCHAR(255),
    target_market VARCHAR(50) NOT NULL DEFAULT 'US',
    language VARCHAR(50) NOT NULL DEFAULT 'en',

    selling_points JSONB,
    pain_points JSONB,
    target_audience JSONB,
    scenes JSONB,
    recommended_video_types JSONB,
    video_score INT,
    risk_tips JSONB,

    status VARCHAR(50) NOT NULL DEFAULT 'active',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_products_video_score CHECK (
        video_score IS NULL OR (video_score >= 0 AND video_score <= 100)
    ),
    CONSTRAINT chk_products_status CHECK (status IN ('active', 'archived', 'deleted'))
);

CREATE INDEX idx_products_user_id ON products(user_id);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_target_market ON products(target_market);
CREATE INDEX idx_products_created_at ON products(created_at);

-- ============================================================
-- 5. PRODUCT IMAGES
-- ============================================================

CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    url TEXT NOT NULL,
    file_name VARCHAR(255),
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);
CREATE INDEX idx_product_images_user_id ON product_images(user_id);
CREATE UNIQUE INDEX uq_product_images_primary
ON product_images(product_id)
WHERE is_primary = TRUE;

-- ============================================================
-- 6. VIDEO TASKS
-- ============================================================

CREATE TABLE video_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,

    status VARCHAR(80) NOT NULL DEFAULT 'draft',
    progress INT NOT NULL DEFAULT 0,

    duration INT NOT NULL,
    video_type VARCHAR(100) NOT NULL,
    task_mode VARCHAR(80) NOT NULL DEFAULT 'PRODUCT_CREATIVE',
    product_category VARCHAR(80) NOT NULL DEFAULT 'general',
    shot_count INT,
    current_version INT NOT NULL DEFAULT 1,
    need_subtitles BOOLEAN NOT NULL DEFAULT TRUE,
    need_voiceover BOOLEAN NOT NULL DEFAULT FALSE,

    selected_plan_id UUID,
    asset_analysis JSONB,
    render_manifest JSONB,
    manifest_version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    schema_version VARCHAR(50) NOT NULL DEFAULT '1.0.0',

    failed_stage VARCHAR(100),
    error_code VARCHAR(100),
    error_message TEXT,
    error_retryable BOOLEAN,
    retry_count INT NOT NULL DEFAULT 0,

    ai_workflow_id VARCHAR(255),
    render_task_id VARCHAR(255),

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_video_tasks_status CHECK (
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
    ),
    CONSTRAINT chk_video_tasks_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT chk_video_tasks_duration CHECK (duration IN (15, 20, 25, 30)),
    CONSTRAINT chk_video_tasks_video_type CHECK (
        video_type IN (
            'pain_point_solution',
            'before_after',
            'review',
            'product_showcase',
            'ugc_style',
            'tutorial'
        )
    ),
    CONSTRAINT chk_video_tasks_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_video_tasks_task_mode CHECK (
        task_mode IN (
            'PRODUCT_CREATIVE',
            'REFERENCE_STORYBOARD',
            'USER_SCRIPT',
            'CUSTOM_STORYBOARD'
        )
    ),
    CONSTRAINT chk_video_tasks_shot_count CHECK (
        shot_count IS NULL OR shot_count > 0
    ),
    CONSTRAINT chk_video_tasks_current_version CHECK (current_version >= 1)
);

CREATE INDEX idx_video_tasks_user_id ON video_tasks(user_id);
CREATE INDEX idx_video_tasks_product_id ON video_tasks(product_id);
CREATE INDEX idx_video_tasks_status ON video_tasks(status);
CREATE INDEX idx_video_tasks_created_at ON video_tasks(created_at);
CREATE INDEX idx_video_tasks_ai_workflow_id ON video_tasks(ai_workflow_id);

-- ============================================================
-- 7. VIDEO PLANS
-- ============================================================

CREATE TABLE video_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    hook TEXT NOT NULL,
    structure TEXT,
    reason TEXT,
    estimated_duration INT,
    score INT,

    raw_ai_output JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_video_plans_score CHECK (
        score IS NULL OR (score >= 0 AND score <= 100)
    )
);

CREATE INDEX idx_video_plans_task_id ON video_plans(task_id);
CREATE INDEX idx_video_plans_user_id ON video_plans(user_id);
CREATE INDEX idx_video_plans_type ON video_plans(type);

ALTER TABLE video_tasks
ADD CONSTRAINT fk_video_tasks_selected_plan
FOREIGN KEY (selected_plan_id) REFERENCES video_plans(id) ON DELETE SET NULL;

-- ============================================================
-- 8. STORYBOARDS
-- ============================================================

CREATE TABLE storyboards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    plan_id UUID REFERENCES video_plans(id) ON DELETE SET NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title VARCHAR(255) NOT NULL,
    hook TEXT NOT NULL,
    script TEXT,
    subtitles JSONB,
    cover_text VARCHAR(255),
    caption TEXT,
    hashtags JSONB,
    music_suggestion TEXT,

    raw_ai_output JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_storyboards_task_id ON storyboards(task_id);
CREATE INDEX idx_storyboards_user_id ON storyboards(user_id);
CREATE INDEX idx_storyboards_plan_id ON storyboards(plan_id);

-- ============================================================
-- 9. STORYBOARD SHOTS
-- ============================================================

CREATE TABLE storyboard_shots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storyboard_id UUID NOT NULL REFERENCES storyboards(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    shot_no INT NOT NULL,
    duration INT NOT NULL,

    scene TEXT NOT NULL,
    action TEXT,
    subtitle TEXT NOT NULL,
    material_type VARCHAR(100) NOT NULL,
    prompt TEXT,
    negative_prompt TEXT,
    edit_instruction TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_storyboard_shots_duration CHECK (duration > 0),
    CONSTRAINT chk_storyboard_shots_material_type CHECK (
        material_type IN (
            'product_image',
            'product_image_motion',
            'ai_image',
            'ai_video',
            'text_animation',
            'uploaded_video'
        )
    )
);

CREATE INDEX idx_storyboard_shots_storyboard_id ON storyboard_shots(storyboard_id);
CREATE INDEX idx_storyboard_shots_task_id ON storyboard_shots(task_id);
CREATE UNIQUE INDEX uq_storyboard_shot_no ON storyboard_shots(storyboard_id, shot_no);

-- ============================================================
-- 10. MATERIALS
-- ============================================================

CREATE TABLE materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    shot_id UUID REFERENCES storyboard_shots(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    type VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    prompt TEXT,
    negative_prompt TEXT,

    provider VARCHAR(100),
    model_name VARCHAR(100),

    status VARCHAR(50) NOT NULL DEFAULT 'completed',
    quality_score INT,
    error_message TEXT,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_materials_type CHECK (
        type IN (
            'image',
            'video',
            'product_image',
            'cover_image',
            'audio',
            'subtitle'
        )
    ),
    CONSTRAINT chk_materials_status CHECK (
        status IN ('generating', 'completed', 'failed', 'replaced')
    ),
    CONSTRAINT chk_materials_quality_score CHECK (
        quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100)
    )
);

CREATE INDEX idx_materials_task_id ON materials(task_id);
CREATE INDEX idx_materials_user_id ON materials(user_id);
CREATE INDEX idx_materials_shot_id ON materials(shot_id);
CREATE INDEX idx_materials_status ON materials(status);

-- ============================================================
-- 11. VIDEOS
-- ============================================================

CREATE TABLE videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title VARCHAR(255) NOT NULL,
    video_url TEXT NOT NULL,
    cover_url TEXT,

    duration INT NOT NULL,
    resolution VARCHAR(50) NOT NULL,
    fps INT NOT NULL DEFAULT 30,

    quality_score INT,
    risk_score INT,

    caption TEXT,
    hashtags JSONB,

    status VARCHAR(50) NOT NULL DEFAULT 'completed',
    exported_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_videos_quality_score CHECK (
        quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100)
    ),
    CONSTRAINT chk_videos_risk_score CHECK (
        risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)
    ),
    CONSTRAINT chk_videos_status CHECK (
        status IN ('completed', 'exported', 'deleted')
    )
);

CREATE INDEX idx_videos_task_id ON videos(task_id);
CREATE INDEX idx_videos_product_id ON videos(product_id);
CREATE INDEX idx_videos_user_id ON videos(user_id);
CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_videos_created_at ON videos(created_at);

-- ============================================================
-- 12. MODEL LOGS
-- ============================================================

CREATE TABLE model_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES video_tasks(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    service VARCHAR(100) NOT NULL DEFAULT 'python-ai',
    provider VARCHAR(100),
    model_name VARCHAR(100),
    task_type VARCHAR(100) NOT NULL,

    input_tokens INT,
    output_tokens INT,
    cost NUMERIC(12, 6),

    status VARCHAR(50) NOT NULL,
    error_message TEXT,

    request_summary JSONB,
    response_summary JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_model_logs_status CHECK (status IN ('success', 'failed'))
);

CREATE INDEX idx_model_logs_task_id ON model_logs(task_id);
CREATE INDEX idx_model_logs_user_id ON model_logs(user_id);
CREATE INDEX idx_model_logs_task_type ON model_logs(task_type);
CREATE INDEX idx_model_logs_created_at ON model_logs(created_at);

-- ============================================================
-- 13. RENDER LOGS
-- ============================================================

CREATE TABLE render_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES video_tasks(id) ON DELETE SET NULL,
    video_id UUID REFERENCES videos(id) ON DELETE SET NULL,

    render_task_id VARCHAR(255),
    template VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,

    duration_seconds INT,
    output_url TEXT,
    cover_url TEXT,

    error_message TEXT,
    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_render_logs_status CHECK (status IN ('success', 'failed'))
);

CREATE INDEX idx_render_logs_task_id ON render_logs(task_id);
CREATE INDEX idx_render_logs_video_id ON render_logs(video_id);
CREATE INDEX idx_render_logs_status ON render_logs(status);
CREATE INDEX idx_render_logs_created_at ON render_logs(created_at);

-- ============================================================
-- 14. QUOTA RECORDS
-- ============================================================

CREATE TABLE quota_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES video_tasks(id) ON DELETE SET NULL,

    type VARCHAR(100) NOT NULL,
    amount INT NOT NULL,
    direction VARCHAR(50) NOT NULL,
    reason VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quota_records_type CHECK (
        type IN ('video', 'image', 'video_clip', 'export')
    ),
    CONSTRAINT chk_quota_records_direction CHECK (
        direction IN ('consume', 'refund', 'grant')
    ),
    CONSTRAINT chk_quota_records_amount CHECK (amount > 0)
);

CREATE INDEX idx_quota_records_user_id ON quota_records(user_id);
CREATE INDEX idx_quota_records_task_id ON quota_records(task_id);
CREATE INDEX idx_quota_records_created_at ON quota_records(created_at);
CREATE UNIQUE INDEX uq_quota_records_idempotency_key
ON quota_records(idempotency_key);

-- ============================================================
-- 15. REFRESH TOKENS
-- ============================================================

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- ============================================================
-- 16. FASHION CREATIVE LOOP EXTENSION (video_tasks ALTER)
-- ============================================================

-- video_tasks extended columns for Fashion Creative Loop V1:
-- ALTER TABLE video_tasks
--     ADD COLUMN task_mode VARCHAR(80) NOT NULL DEFAULT 'PRODUCT_CREATIVE',
--     ADD COLUMN product_category VARCHAR(80) NOT NULL DEFAULT 'general',
--     ADD COLUMN shot_count INT,
--     ADD COLUMN current_version INT NOT NULL DEFAULT 1,
--     ADD CONSTRAINT chk_video_tasks_task_mode CHECK (
--         task_mode IN ('PRODUCT_CREATIVE', 'REFERENCE_STORYBOARD', 'USER_SCRIPT', 'CUSTOM_STORYBOARD')
--     ),
--     ADD CONSTRAINT chk_video_tasks_shot_count CHECK (
--         shot_count IS NULL OR shot_count > 0
--     ),
--     ADD CONSTRAINT chk_video_tasks_current_version CHECK (current_version >= 1);

-- Also extend the status CHECK to include fashion creative loop statuses:
-- ALTER TABLE video_tasks DROP CONSTRAINT chk_video_tasks_status;
-- ALTER TABLE video_tasks ADD CONSTRAINT chk_video_tasks_status CHECK (
--     status IN (
--         'draft', 'asset_uploading', 'asset_analyzing',
--         'waiting_asset_confirmation', 'reference_analyzing',
--         'plan_generating', 'analyzing', 'analysis_completed',
--         'plan_generated', 'waiting_plan_selection',
--         'storyboard_generating', 'script_generating', 'script_generated',
--         'material_generating', 'material_generated', 'rendering', 'checking',
--         'completed', 'failed', 'exported',
--         -- Fashion Creative Loop:
--         'waiting_storyboard_confirmation', 'keyframe_configuring',
--         'image_generating', 'waiting_image_confirmation',
--         'video_clip_generating', 'waiting_video_clip_confirmation',
--         'waiting_final_review', 'repairing', 'cancelled'
--     )
-- );

-- ============================================================
-- 17. TASK ASSETS (Fashion Creative Loop)
-- ============================================================

CREATE TABLE task_assets (
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

    CONSTRAINT chk_task_assets_type CHECK (
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

CREATE INDEX idx_task_assets_task_id ON task_assets(task_id);
CREATE INDEX idx_task_assets_user_id ON task_assets(user_id);
CREATE INDEX idx_task_assets_product_id ON task_assets(product_id);
CREATE INDEX idx_task_assets_role ON task_assets(asset_role);

-- ============================================================
-- 18. CREATIVE STATES (Fashion Creative Loop)
-- ============================================================

CREATE TABLE creative_states (
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

CREATE UNIQUE INDEX uq_creative_states_task_id ON creative_states(task_id);

-- ============================================================
-- 19. KEYFRAMES (Fashion Creative Loop)
-- ============================================================

CREATE TABLE keyframes (
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

CREATE INDEX idx_keyframes_task_id ON keyframes(task_id);
CREATE INDEX idx_keyframes_storyboard_id ON keyframes(storyboard_id);
CREATE INDEX idx_keyframes_shot_id ON keyframes(shot_id);
CREATE INDEX idx_keyframes_user_id ON keyframes(user_id);
CREATE INDEX idx_keyframes_status ON keyframes(status);
CREATE UNIQUE INDEX uq_keyframes_task_shot_version
    ON keyframes(task_id, shot_no, version);

-- ============================================================
-- 20. VIDEO CLIPS (Fashion Creative Loop)
-- ============================================================

CREATE TABLE video_clips (
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
    CONSTRAINT chk_video_clips_duration CHECK (
        duration > 0
    )
);

CREATE INDEX idx_video_clips_task_id ON video_clips(task_id);
CREATE INDEX idx_video_clips_storyboard_id ON video_clips(storyboard_id);
CREATE INDEX idx_video_clips_shot_id ON video_clips(shot_id);
CREATE INDEX idx_video_clips_keyframe_id ON video_clips(keyframe_id);
CREATE INDEX idx_video_clips_user_id ON video_clips(user_id);
CREATE INDEX idx_video_clips_status ON video_clips(status);
CREATE UNIQUE INDEX uq_video_clips_task_shot_version
    ON video_clips(task_id, shot_no, version);

-- ============================================================
-- 21. REPAIR EVENTS (Fashion Creative Loop)
-- ============================================================

CREATE TABLE repair_events (
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

CREATE INDEX idx_repair_events_task_id ON repair_events(task_id);
CREATE INDEX idx_repair_events_user_id ON repair_events(user_id);
CREATE INDEX idx_repair_events_status ON repair_events(status);

-- ============================================================
-- 22. VIDEO FINGERPRINTS (Fashion Creative Loop)
-- ============================================================

CREATE TABLE video_fingerprints (
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

CREATE INDEX idx_video_fingerprints_task_id ON video_fingerprints(task_id);
CREATE INDEX idx_video_fingerprints_product_id ON video_fingerprints(product_id);
CREATE INDEX idx_video_fingerprints_user_id ON video_fingerprints(user_id);

-- ============================================================
-- 23. QA RESULTS (Fashion Creative Loop)
-- ============================================================

CREATE TABLE qa_results (
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

CREATE INDEX idx_qa_results_task_id ON qa_results(task_id);
CREATE INDEX idx_qa_results_user_id ON qa_results(user_id);
CREATE INDEX idx_qa_results_target_type ON qa_results(target_type);

-- ============================================================
-- 24. V2+ EXTENSION TABLES
-- These tables can be created later when V2/V3/V4 starts.
-- Recommendation:
-- Keep V1 production migrations limited to sections 1-15.
-- Move the following extension tables into a separate migration file such as
-- 02-v2-extension-schema.sql before implementing V1.
-- ============================================================

/*
-- V2+ reference schema starts here. Do not execute this block in V1 migrations.

CREATE TABLE batch_video_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,

    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    total_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,

    config JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_batch_video_tasks_user_id ON batch_video_tasks(user_id);
CREATE INDEX idx_batch_video_tasks_status ON batch_video_tasks(status);

CREATE TABLE competitor_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,

    source_url TEXT,
    uploaded_file_url TEXT,
    title TEXT,
    platform VARCHAR(100) DEFAULT 'tiktok',
    raw_metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_competitor_videos_user_id ON competitor_videos(user_id);

CREATE TABLE video_analysis_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competitor_video_id UUID NOT NULL REFERENCES competitor_videos(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    hook_analysis JSONB,
    structure_analysis JSONB,
    subtitle_analysis JSONB,
    rhythm_analysis JSONB,
    reusable_points JSONB,
    risk_tips JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_analysis_results_competitor_video_id
ON video_analysis_results(competitor_video_id);

CREATE TABLE video_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    views INT DEFAULT 0,
    likes INT DEFAULT 0,
    comments INT DEFAULT 0,
    shares INT DEFAULT 0,
    saves INT DEFAULT 0,
    product_clicks INT DEFAULT 0,
    orders INT DEFAULT 0,
    gmv NUMERIC(12, 2) DEFAULT 0,

    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_metrics_video_id ON video_metrics(video_id);
CREATE INDEX idx_video_metrics_user_id ON video_metrics(user_id);

CREATE TABLE shops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,
    platform VARCHAR(100) NOT NULL DEFAULT 'tiktok_shop',
    target_market VARCHAR(50),
    category VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'active',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shops_user_id ON shops(user_id);

CREATE TABLE social_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shop_id UUID REFERENCES shops(id) ON DELETE SET NULL,

    account_name VARCHAR(255) NOT NULL,
    platform VARCHAR(100) NOT NULL DEFAULT 'tiktok',
    account_type VARCHAR(100),
    target_market VARCHAR(50),
    content_style VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'active',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_social_accounts_user_id ON social_accounts(user_id);
CREATE INDEX idx_social_accounts_shop_id ON social_accounts(shop_id);

CREATE TABLE publish_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    shop_id UUID REFERENCES shops(id) ON DELETE SET NULL,
    account_id UUID REFERENCES social_accounts(id) ON DELETE SET NULL,

    scheduled_time TIMESTAMPTZ NOT NULL,
    caption TEXT,
    hashtags JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'scheduled',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_publish_schedules_user_id ON publish_schedules(user_id);
CREATE INDEX idx_publish_schedules_status ON publish_schedules(status);
CREATE INDEX idx_publish_schedules_scheduled_time ON publish_schedules(scheduled_time);

-- V2+ reference schema ends here.
*/
