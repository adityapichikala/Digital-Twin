-- ==============================================================================
-- V2__simulation_audit.sql
-- Audit log for what-if simulation executions and results
-- ==============================================================================

CREATE TABLE IF NOT EXISTS simulation_audit (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id     UUID NOT NULL,
    user_id           VARCHAR(200) NOT NULL DEFAULT 'anonymous',
    cluster_name      VARCHAR(200) NOT NULL DEFAULT 'twindigital-cluster',
    change_set        JSONB NOT NULL,
    result            JSONB NOT NULL,
    risk_level        VARCHAR(50) NOT NULL,
    score             INT NOT NULL,
    predicted_outcome TEXT,
    findings_count    INT NOT NULL DEFAULT 0,
    executed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_simulation_audit_user ON simulation_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_simulation_audit_cluster ON simulation_audit(cluster_name);
CREATE INDEX IF NOT EXISTS idx_simulation_audit_time ON simulation_audit(executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_simulation_audit_risk ON simulation_audit(risk_level);
