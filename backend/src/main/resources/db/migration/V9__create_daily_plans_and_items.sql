CREATE TABLE daily_plans (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_date           DATE NOT NULL,
    budget_minutes      INT NOT NULL,
    total_estimated_min INT NOT NULL,
    total_value         DOUBLE PRECISION NOT NULL,
    overdue_total       INT DEFAULT 0,
    overdue_shown       INT DEFAULT 0,
    generated_at        TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, plan_date)
);

CREATE TABLE daily_plan_items (
    id                  BIGSERIAL PRIMARY KEY,
    plan_id             BIGINT NOT NULL REFERENCES daily_plans(id) ON DELETE CASCADE,
    problem_id          BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    item_type           VARCHAR(15) NOT NULL,        -- RETRY | REVISION | NEW_PROBLEM
    display_order       INT NOT NULL,
    estimated_minutes   INT NOT NULL,
    value_score         DOUBLE PRECISION NOT NULL,
    value_json          JSONB,
    reason_summary      TEXT NOT NULL,
    follow_up_type      VARCHAR(30),
    follow_up_detail    TEXT,
    completed           BOOLEAN DEFAULT FALSE,
    completed_at        TIMESTAMP,
    attempt_id          BIGINT REFERENCES user_problem_attempts(id) ON DELETE SET NULL,
    skipped             BOOLEAN DEFAULT FALSE,
    skipped_reason      TEXT
);

CREATE INDEX idx_daily_plans_user_date ON daily_plans(user_id, plan_date);
CREATE INDEX idx_daily_plan_items_plan ON daily_plan_items(plan_id);
CREATE INDEX idx_daily_plan_items_problem ON daily_plan_items(problem_id);
