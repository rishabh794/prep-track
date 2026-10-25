CREATE TABLE sheets (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,
    source_url      VARCHAR(500),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE sheet_problems (
    id              BIGSERIAL PRIMARY KEY,
    sheet_id        BIGINT NOT NULL REFERENCES sheets(id) ON DELETE CASCADE,
    problem_id      BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    category        VARCHAR(100),
    display_order   INT NOT NULL,
    UNIQUE (sheet_id, problem_id)
);

CREATE TABLE user_sheets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sheet_id    BIGINT NOT NULL REFERENCES sheets(id) ON DELETE CASCADE,
    selected_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, sheet_id)
);

CREATE TABLE applications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    role            VARCHAR(255),
    status          VARCHAR(30) DEFAULT 'WISHLIST',
                    -- WISHLIST | APPLIED | OA | INTERVIEW | OFFER | REJECTED
    applied_date    DATE,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE interviews (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interview_date  DATE NOT NULL,
    round_name      VARCHAR(100),
    raw_feedback    TEXT,
    ai_extracted    BOOLEAN DEFAULT FALSE,
    outcome         VARCHAR(20) DEFAULT 'UPCOMING',
                    -- UPCOMING | PASSED | FAILED | PENDING
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE interview_topics (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    pattern_id      BIGINT NOT NULL REFERENCES patterns(id) ON DELETE CASCADE,
    was_weak        BOOLEAN DEFAULT FALSE,
    confirmed       BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_sheet_problems_sheet ON sheet_problems(sheet_id);
CREATE INDEX idx_user_sheets_user ON user_sheets(user_id);
CREATE INDEX idx_applications_user ON applications(user_id);
CREATE INDEX idx_interviews_app ON interviews(application_id);
CREATE INDEX idx_interviews_date ON interviews(interview_date);
