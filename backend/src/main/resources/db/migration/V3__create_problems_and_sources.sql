CREATE TABLE problems (
    id                  BIGSERIAL PRIMARY KEY,
    canonical_title     VARCHAR(255) NOT NULL,
    difficulty          VARCHAR(10) NOT NULL,        -- EASY, MEDIUM, HARD
    estimated_minutes   INT NOT NULL DEFAULT 30,
    description_hint    TEXT,                         -- brief problem summary for recall
    reference_approach  TEXT,                         -- known solution approach(es) for AI grading
    optimal_complexity  VARCHAR(100),                 -- e.g., "O(n) time, O(1) space"
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE problem_sources (
    id              BIGSERIAL PRIMARY KEY,
    problem_id      BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    platform        VARCHAR(30) NOT NULL,            -- LEETCODE, GFG, CODEFORCES, CUSTOM
    external_id     VARCHAR(150),                    -- "1" for LC #1, "two-sum" slug for GFG
    external_url    VARCHAR(500) NOT NULL,
    external_title  VARCHAR(255),
    UNIQUE (platform, external_id)
);

CREATE INDEX idx_problems_difficulty ON problems(difficulty);
CREATE INDEX idx_problem_sources_lookup ON problem_sources(platform, external_id);
