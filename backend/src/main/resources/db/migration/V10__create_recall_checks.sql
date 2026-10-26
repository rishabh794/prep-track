CREATE TABLE recall_checks (
    id                  BIGSERIAL PRIMARY KEY,
    revision_card_id    BIGINT NOT NULL REFERENCES revision_cards(id) ON DELETE CASCADE,
    user_approach       TEXT NOT NULL,
    ai_assessment       VARCHAR(10),                 -- PASS | PARTIAL | FAIL
    ai_reasoning        TEXT,
    user_override       VARCHAR(10),
    final_grade         VARCHAR(10) NOT NULL,
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_recall_checks_revision_card ON recall_checks(revision_card_id);
