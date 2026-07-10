-- V7: Sync creative_states table with Entity expectations
-- Actual DB has: current_stage, plan_context, storyboard_context, generation_params, reference_analysis
-- Entity expects: product_json, model_json, scene_json, outfit_json, reference_video_json, constraints_json, user_requirements_json

-- Add user_id (required by Entity)
ALTER TABLE creative_states
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Add Entity-expected JSONB columns
ALTER TABLE creative_states
    ADD COLUMN IF NOT EXISTS product_json JSONB,
    ADD COLUMN IF NOT EXISTS model_json JSONB,
    ADD COLUMN IF NOT EXISTS scene_json JSONB,
    ADD COLUMN IF NOT EXISTS outfit_json JSONB,
    ADD COLUMN IF NOT EXISTS reference_video_json JSONB,
    ADD COLUMN IF NOT EXISTS constraints_json JSONB,
    ADD COLUMN IF NOT EXISTS user_requirements_json JSONB;
