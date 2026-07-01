-- Drop old task_mode constraint from M1-era V3 (manual/ai_assisted/auto).
-- Application code now enforces valid task_mode values.
-- Remove rather than replace to avoid issues with existing rows.
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS chk_video_tasks_task_mode;
