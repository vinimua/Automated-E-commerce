-- Fix chk_keyframes_status to include the values the Java code actually uses.
-- The V3 migration file specified one set, but the live DB had a different set.
ALTER TABLE keyframes DROP CONSTRAINT IF EXISTS chk_keyframes_status;
ALTER TABLE keyframes ADD CONSTRAINT chk_keyframes_status CHECK (
    status IN ('draft', 'pending', 'generating', 'generated', 'uploaded', 'confirmed', 'rejected', 'failed')
);
