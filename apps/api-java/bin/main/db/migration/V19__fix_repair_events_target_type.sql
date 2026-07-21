-- Fix chk_repair_events_target_type to include final_video and render_manifest.
ALTER TABLE repair_events DROP CONSTRAINT IF EXISTS chk_repair_events_target_type;
ALTER TABLE repair_events ADD CONSTRAINT chk_repair_events_target_type CHECK (
    target_type IN ('storyboard', 'keyframe', 'video_clip', 'plan', 'render_manifest', 'final_video')
);
