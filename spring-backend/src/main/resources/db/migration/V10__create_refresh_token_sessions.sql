CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_refresh_session_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_refresh_sessions_member ON refresh_sessions(member_id);
CREATE INDEX idx_refresh_sessions_expiry ON refresh_sessions(expires_at);

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES refresh_sessions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_refresh_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens(session_id);
