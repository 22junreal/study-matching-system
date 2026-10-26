CREATE TABLE study_members (
    id BIGSERIAL PRIMARY KEY,
    study_id BIGINT NOT NULL REFERENCES studies(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_study_members_study_member UNIQUE (study_id, member_id)
);
CREATE INDEX idx_study_members_study_status ON study_members(study_id, status);

-- The owner counts as a participant. Keep applications as application history.
INSERT INTO study_members (study_id, member_id, status, created_at, updated_at)
SELECT id, owner_id, 'ACTIVE', created_at, updated_at FROM studies;

INSERT INTO study_members (study_id, member_id, status, created_at, updated_at)
SELECT study_id, applicant_id, 'ACTIVE', created_at, updated_at
FROM study_applications WHERE status = 'APPROVED'
ON CONFLICT (study_id, member_id) DO NOTHING;
