-- Add 8-second duration for testing/development (fewer shots, lower token cost)
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS chk_video_tasks_duration;
ALTER TABLE video_tasks ADD CONSTRAINT chk_video_tasks_duration CHECK (duration IN (8, 15, 20, 25, 30));
