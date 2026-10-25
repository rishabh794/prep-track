CREATE TABLE problem_patterns (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    pattern_id  BIGINT NOT NULL REFERENCES patterns(id) ON DELETE CASCADE,
    weight      DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    UNIQUE (problem_id, pattern_id)
);

CREATE INDEX idx_problem_patterns_pattern ON problem_patterns(pattern_id);
