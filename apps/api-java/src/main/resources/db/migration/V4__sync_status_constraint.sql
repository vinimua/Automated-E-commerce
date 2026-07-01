-- ============================================================
-- V4: Sync video_tasks constraints with current code
-- Fixes mismatch between V3 migration file and applied DB state:
--   - chk_video_tasks_status: was missing asset_uploading etc.
--   - chk_video_tasks_task_mode: was manual/ai_assisted/auto
-- ============================================================

-- Fix status constraint
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS chk_video_tasks_status;

ALTER TABLE video_tasks ADD CONSTRAINT chk_video_tasks_status CHECK (
    status IN (
        -- V1 Core
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
        'exported',
        -- Fashion Creative Loop V1
        'asset_uploading',
        'asset_analyzing',
        'waiting_asset_confirmation',
        'reference_analyzing',
        'plan_generating',
        'storyboard_generating',
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

-- Fix task_mode constraint (old DB has manual/ai_assisted/auto)
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS chk_video_tasks_task_mode;

ALTER TABLE video_tasks ADD CONSTRAINT chk_video_tasks_task_mode CHECK (
    task_mode IN (
        'PRODUCT_CREATIVE',
        'REFERENCE_STORYBOARD',
        'USER_SCRIPT',
        'CUSTOM_STORYBOARD'
    )
);
