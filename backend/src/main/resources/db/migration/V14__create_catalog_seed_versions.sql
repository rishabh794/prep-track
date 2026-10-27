CREATE TABLE catalog_seed_versions (
    version VARCHAR(100) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE sheets ADD COLUMN expected_problem_count INTEGER;
ALTER TABLE sheets ADD COLUMN seed_version VARCHAR(100);
