-- Simulation runs
CREATE TABLE IF NOT EXISTS td_simulations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name    VARCHAR(200) NOT NULL,
    created_by      VARCHAR(200),
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETE, FAILED
    change_set      JSONB        NOT NULL, -- the what-if changes
    result          JSONB,                 -- populated on completion
    risk_score      INT,
    cost_delta_usd  DOUBLE PRECISION,
    explanations    JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_simulations_cluster ON td_simulations(cluster_name);
CREATE INDEX idx_simulations_status  ON td_simulations(status);
