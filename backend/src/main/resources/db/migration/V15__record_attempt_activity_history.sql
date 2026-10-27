ALTER TABLE user_problem_attempts ADD COLUMN activity_type VARCHAR(30) NOT NULL DEFAULT 'CODING_ATTEMPT';
ALTER TABLE user_problem_attempts ADD COLUMN recall_check_id BIGINT;
UPDATE user_problem_attempts SET activity_type = 'CODING_REVIEW' WHERE is_revision = TRUE;
CREATE INDEX idx_attempt_history_user_time ON user_problem_attempts(user_id, attempted_at DESC, id DESC);
CREATE INDEX idx_attempt_history_user_problem_time ON user_problem_attempts(user_id, problem_id, attempted_at DESC, id DESC);
CREATE UNIQUE INDEX idx_attempt_history_recall_check ON user_problem_attempts(recall_check_id);
