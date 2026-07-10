-- V9: Add missing columns to video_clips table
ALTER TABLE video_clips
    ADD COLUMN IF NOT EXISTS storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_video_clips_storyboard_id ON video_clips(storyboard_id);
