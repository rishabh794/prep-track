ALTER TABLE interviews ADD COLUMN topics_confirmed_at TIMESTAMP;

-- Preserve confirmation state for existing feedback, independently of editable interview outcome.
UPDATE interviews
SET topics_confirmed_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE EXISTS (SELECT 1 FROM interview_topics topic WHERE topic.interview_id = interviews.id AND topic.confirmed = TRUE);
