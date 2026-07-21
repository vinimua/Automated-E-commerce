-- Fix chk_videos_status to include the values the Java code uses.
ALTER TABLE videos DROP CONSTRAINT IF EXISTS chk_videos_status;
ALTER TABLE videos ADD CONSTRAINT chk_videos_status CHECK (
    status IN ('completed', 'exported', 'deleted', 'waiting_review', 'draft', 'rendering')
);
