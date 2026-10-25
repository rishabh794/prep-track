CREATE TABLE patterns (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    parent_topic    VARCHAR(100),
    description     TEXT,
    difficulty_tier INT DEFAULT 1
);

CREATE INDEX idx_patterns_slug ON patterns(slug);
