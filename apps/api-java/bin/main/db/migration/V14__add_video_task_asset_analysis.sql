ALTER TABLE video_tasks
    ADD COLUMN IF NOT EXISTS asset_analysis JSONB;

COMMENT ON COLUMN video_tasks.asset_analysis IS
    'Task-scoped structured analysis of the assets confirmed for this video task.';
