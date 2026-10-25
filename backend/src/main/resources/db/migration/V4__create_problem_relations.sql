CREATE TABLE problem_relations (
    id                  BIGSERIAL PRIMARY KEY,
    source_problem_id   BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    target_problem_id   BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    relation_type       VARCHAR(25) NOT NULL,
                        -- VARIANT: same core idea, different constraints (2Sum ↔ 3Sum)
                        -- EASIER_PREREQUISITE: target is an easier warmup for source
    reviewed            BOOLEAN DEFAULT FALSE,
    notes               TEXT,
    UNIQUE (source_problem_id, target_problem_id),
    CHECK (source_problem_id != target_problem_id)
);

CREATE INDEX idx_problem_relations_source ON problem_relations(source_problem_id);
CREATE INDEX idx_problem_relations_target ON problem_relations(target_problem_id);
