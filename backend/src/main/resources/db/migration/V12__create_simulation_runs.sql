CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_json TEXT NOT NULL
);
CREATE INDEX idx_simulation_runs_user_created ON simulation_runs(user_id, created_at);
