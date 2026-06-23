-- ============================================================
-- TikTok Shop AI Video System
-- V1 Initial Schema Migration
-- Flyway migration: V1__initial_schema.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. USERS
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
-- 2. USER QUOTAS
-- ============================================================

CREATE TABLE user_quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    video_quota INT NOT NULL DEFAULT 0,
    image_quota INT NOT NULL DEFAULT 0,
    video_clip_quota INT NOT NULL DEFAULT 0,
    export_quota INT NOT NULL DEFAULT 0,

    used_video_count INT NOT NULL DEFAULT 0,
    used_image_count INT NOT NULL DEFAULT 0,
    used_video_clip_count INT NOT NULL DEFAULT 0,
    used_export_count INT NOT NULL DEFAULT 0,

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
-- 3. PRODUCTS
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
-- 4. PRODUCT IMAGES
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
-- 5. VIDEO TASKS
-- ============================================================

CREATE TABLE video_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,

    status VARCHAR(80) NOT NULL DEFAULT 'draft',
    progress INT NOT NULL DEFAULT 0,

    duration INT NOT NULL,
    video_type VARCHAR(100) NOT NULL,
    need_subtitles BOOLEAN NOT NULL DEFAULT TRUE,
    need_voiceover BOOLEAN NOT NULL DEFAULT FALSE,

    selected_plan_id UUID,
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
            'analyzing',
            'analysis_completed',
            'plan_generated',
            'waiting_plan_selection',
            'script_generating',
            'script_generated',
            'material_generating',
            'material_generated',
            'rendering',
            'checking',
            'completed',
            'failed',
            'exported'
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
    CONSTRAINT chk_video_tasks_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_video_tasks_user_id ON video_tasks(user_id);
CREATE INDEX idx_video_tasks_product_id ON video_tasks(product_id);
CREATE INDEX idx_video_tasks_status ON video_tasks(status);
CREATE INDEX idx_video_tasks_created_at ON video_tasks(created_at);
CREATE INDEX idx_video_tasks_ai_workflow_id ON video_tasks(ai_workflow_id);

-- ============================================================
-- 6. VIDEO PLANS
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
-- 7. STORYBOARDS
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
-- 8. STORYBOARD SHOTS
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
-- 9. MATERIALS
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
-- 10. VIDEOS
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
-- 11. MODEL LOGS
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
-- 12. RENDER LOGS
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
-- 13. QUOTA RECORDS
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
-- 14. REFRESH TOKENS
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
