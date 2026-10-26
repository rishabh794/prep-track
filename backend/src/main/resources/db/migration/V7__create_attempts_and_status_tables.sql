CREATE TABLE user_problem_status (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id          BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL DEFAULT 'UNSOLVED',
                        -- UNSOLVED | ATTEMPTED | SOLVED_WITH_HELP | SOLVED_INDEPENDENTLY
    total_attempts      INT DEFAULT 0,
    independent_solves  INT DEFAULT 0,
    last_outcome        VARCHAR(30),
    last_failure_reason VARCHAR(30),
    last_attempted_at   TIMESTAMP,
    first_solved_at     TIMESTAMP,
    insight_note        TEXT,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, problem_id)
);

CREATE TABLE user_problem_attempts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id          BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    outcome             VARCHAR(30) NOT NULL,
                        -- SOLVED_INDEPENDENTLY | SOLVED_WITH_HELP | COULD_NOT_SOLVE
    failure_reason      VARCHAR(30),
                        -- PATTERN_GAP | IMPLEMENTATION_GAP | EDGE_CASE_MISS | TOO_SLOW
    time_taken_sec      INT,
    hints_used          INT DEFAULT 0,
    is_revision         BOOLEAN DEFAULT FALSE,
    follow_up_type      VARCHAR(30),
                        -- EASIER_PREREQUISITE | CODE_FROM_SCRATCH | EDGE_CASE_LIST | TIMED_RETRY
    notes               TEXT,
    attempted_at        TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_problem_status_user ON user_problem_status(user_id);
CREATE INDEX idx_user_problem_status_problem ON user_problem_status(problem_id);
CREATE INDEX idx_user_problem_status_status ON user_problem_status(status);

CREATE INDEX idx_user_problem_attempts_user ON user_problem_attempts(user_id);
CREATE INDEX idx_user_problem_attempts_problem ON user_problem_attempts(problem_id);
CREATE INDEX idx_user_problem_attempts_date ON user_problem_attempts(attempted_at);
