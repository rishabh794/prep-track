CREATE TABLE company_profiles (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    provenance VARCHAR(30) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    source_notes TEXT NOT NULL,
    seed_checksum VARCHAR(64) NOT NULL
);

CREATE TABLE company_aliases (
    id BIGSERIAL PRIMARY KEY,
    company_profile_id BIGINT NOT NULL REFERENCES company_profiles(id) ON DELETE CASCADE,
    alias VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE company_pattern_weights (
    id BIGSERIAL PRIMARY KEY,
    company_profile_id BIGINT NOT NULL REFERENCES company_profiles(id) ON DELETE CASCADE,
    pattern_id BIGINT NOT NULL REFERENCES patterns(id) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL CHECK (weight > 0 AND weight <= 5),
    UNIQUE (company_profile_id, pattern_id)
);

CREATE INDEX idx_company_aliases_profile ON company_aliases(company_profile_id);
CREATE INDEX idx_company_pattern_weights_profile ON company_pattern_weights(company_profile_id);
