-- Fix chk_video_clips_status to include the values the Java code uses.
ALTER TABLE video_clips DROP CONSTRAINT IF EXISTS chk_video_clips_status;
ALTER TABLE video_clips ADD CONSTRAINT chk_video_clips_status CHECK (
    status IN ('draft', 'pending', 'generating', 'generated', 'uploaded', 'confirmed', 'rejected', 'failed')
);
