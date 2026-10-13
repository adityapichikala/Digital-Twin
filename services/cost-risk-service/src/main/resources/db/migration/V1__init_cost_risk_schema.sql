-- EC2/EKS node pricing catalog (seeded by a separate job from AWS Pricing API)
CREATE TABLE IF NOT EXISTS td_instance_pricing (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region           VARCHAR(30)  NOT NULL,
    instance_type    VARCHAR(50)  NOT NULL,
    on_demand_price  DOUBLE PRECISION NOT NULL,
    spot_price       DOUBLE PRECISION,
    vcpu             INT,
    memory_gib       DOUBLE PRECISION,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (region, instance_type)
);

-- Cost assessments (one per simulation)
CREATE TABLE IF NOT EXISTS td_cost_assessments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id     UUID         NOT NULL UNIQUE,
    cluster_name      VARCHAR(200) NOT NULL,
    baseline_cost_usd DOUBLE PRECISION,
    proposed_cost_usd DOUBLE PRECISION,
    delta_usd         DOUBLE PRECISION,
    risk_score        INT,
    risk_level        VARCHAR(20), -- LOW, MEDIUM, HIGH, CRITICAL
    explanations      JSONB,
    computed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed a few sample instance prices for local dev
INSERT INTO td_instance_pricing (region, instance_type, on_demand_price, spot_price, vcpu, memory_gib)
VALUES
  ('us-east-1', 'm5.large',   0.096,  0.038, 2,  8.0),
  ('us-east-1', 'm5.xlarge',  0.192,  0.075, 4, 16.0),
  ('us-east-1', 'm5.2xlarge', 0.384,  0.150, 8, 32.0),
  ('us-east-1', 'c5.xlarge',  0.170,  0.065, 4,  8.0),
  ('us-east-1', 'r5.large',   0.126,  0.050, 2, 16.0)
ON CONFLICT DO NOTHING;
