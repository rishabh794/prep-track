CREATE TABLE user_pattern_state (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern_id              BIGINT NOT NULL REFERENCES patterns(id) ON DELETE CASCADE,
    skill                   DOUBLE PRECISION DEFAULT 0.0,
    stability               DOUBLE PRECISION DEFAULT 1.0,
    last_practiced_at       TIMESTAMP,
    total_attempts          INT DEFAULT 0,
    independent_solves      INT DEFAULT 0,
    interview_boost         DOUBLE PRECISION DEFAULT 0.0,
    interview_boost_set_at  TIMESTAMP,
    created_at              TIMESTAMP DEFAULT NOW(),
    updated_at              TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, pattern_id)
);

CREATE TABLE revision_cards (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id          BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    next_review_date    DATE NOT NULL,
    review_count        INT DEFAULT 0,
    last_outcome        VARCHAR(30),
    last_reviewed_at    TIMESTAMP,
    required_follow_up  VARCHAR(30),
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, problem_id)
);

CREATE INDEX idx_user_pattern_state_user ON user_pattern_state(user_id);
CREATE INDEX idx_user_pattern_state_pattern ON user_pattern_state(pattern_id);

CREATE INDEX idx_revision_cards_user_date ON revision_cards(user_id, next_review_date);
CREATE INDEX idx_revision_cards_problem ON revision_cards(problem_id);
