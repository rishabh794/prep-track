-- Migration V11: Add user timezone, interview prep notes, and missing unique constraints

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC';

ALTER TABLE interviews
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE interview_topics
    ADD CONSTRAINT uq_interview_topics_interview_pattern UNIQUE (interview_id, pattern_id);

ALTER TABLE daily_plan_items
    ADD CONSTRAINT uq_daily_plan_items_plan_problem UNIQUE (plan_id, problem_id);
