CREATE TABLE IF NOT EXISTS td_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'VIEWER',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Audit log for auth events (logins, failures, token revocations)
CREATE TABLE IF NOT EXISTS td_auth_audit (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID        REFERENCES td_users(id),
    event_type  VARCHAR(50) NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Revoked JWT IDs (jti) for logout / forced expiry
CREATE TABLE IF NOT EXISTS td_revoked_tokens (
    jti         VARCHAR(64) PRIMARY KEY,
    user_id     UUID        REFERENCES td_users(id),
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_revoked_tokens_expires ON td_revoked_tokens(expires_at);

-- Seed admin user (password: Admin@1234 — bcrypt 12 rounds)
-- CHANGE this password immediately after first login.
INSERT INTO td_users (username, email, password, role)
VALUES (
    'admin',
    'admin@twindigital.local',
    '$2a$12$qEoEkH3LqHZV9t1t5nLnBOqJK9VHJ/LmPf6k4N7vX8z3K.uW0D1Wi',
    'ADMIN'
) ON CONFLICT DO NOTHING;
