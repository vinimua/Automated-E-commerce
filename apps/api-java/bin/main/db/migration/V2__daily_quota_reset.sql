-- Phase 6 quota policy update:
-- - Video task quota is a daily quota and refreshes automatically per user.
-- - A user may create at most 10 video tasks per natural day.
-- - Existing all-zero quota rows are treated as legacy placeholder rows and
--   are upgraded to the V1 daily defaults.

ALTER TABLE user_quotas
ADD COLUMN IF NOT EXISTS quota_date DATE NOT NULL DEFAULT CURRENT_DATE;

ALTER TABLE user_quotas ALTER COLUMN video_quota SET DEFAULT 10;
ALTER TABLE user_quotas ALTER COLUMN image_quota SET DEFAULT 50;
ALTER TABLE user_quotas ALTER COLUMN video_clip_quota SET DEFAULT 10;
ALTER TABLE user_quotas ALTER COLUMN export_quota SET DEFAULT 10;

UPDATE user_quotas
SET
    video_quota = 10,
    image_quota = 50,
    video_clip_quota = 10,
    export_quota = 10,
    quota_date = CURRENT_DATE,
    updated_at = NOW()
WHERE video_quota = 0
  AND image_quota = 0
  AND video_clip_quota = 0
  AND export_quota = 0;
