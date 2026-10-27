ALTER TABLE recall_checks ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE recall_checks ADD COLUMN legacy_invalidated BOOLEAN NOT NULL DEFAULT FALSE;
-- The old schema cannot distinguish confirmed checks from pending checks.
-- Require a fresh grading for all existing checks rather than allowing replay.
UPDATE recall_checks SET legacy_invalidated = TRUE;
