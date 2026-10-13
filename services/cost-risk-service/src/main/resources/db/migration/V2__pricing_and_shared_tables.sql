-- Additional instance pricing seed for ap-south-1 and common EKS types
INSERT INTO td_instance_pricing (region, instance_type, on_demand_price, spot_price, vcpu, memory_gib)
VALUES
  ('ap-south-1', 't3.medium',  0.0416, 0.0152, 2,  4.0),
  ('ap-south-1', 't3.large',   0.0832, 0.0304, 2,  8.0),
  ('ap-south-1', 'm5.large',   0.1010, 0.0400, 2,  8.0),
  ('ap-south-1', 'm5.xlarge',  0.2020, 0.0800, 4, 16.0),
  ('us-east-1',  't3.medium',  0.0416, 0.0152, 2,  4.0),
  ('us-east-1',  't3.large',   0.0832, 0.0304, 2,  8.0)
ON CONFLICT (region, instance_type) DO UPDATE SET
  on_demand_price = EXCLUDED.on_demand_price,
  spot_price = EXCLUDED.spot_price,
  vcpu = EXCLUDED.vcpu,
  memory_gib = EXCLUDED.memory_gib,
  updated_at = NOW();

-- Ensure shared snapshot tables exist for cluster state queries
CREATE TABLE IF NOT EXISTS td_nodes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name       VARCHAR(200) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    allocatable_cpu    VARCHAR(50),
    allocatable_memory VARCHAR(50),
    capacity_cpu       VARCHAR(50),
    capacity_memory    VARCHAR(50),
    ready              BOOLEAN NOT NULL DEFAULT true,
    status             VARCHAR(50) NOT NULL DEFAULT 'Ready',
    labels             JSONB,
    conditions         JSONB,
    last_synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, name)
);

CREATE TABLE IF NOT EXISTS td_pods (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name       VARCHAR(200) NOT NULL,
    namespace          VARCHAR(200) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    node_name          VARCHAR(200),
    owner_kind         VARCHAR(100),
    owner_name         VARCHAR(200),
    cpu_requests       VARCHAR(50),
    memory_requests    VARCHAR(50),
    cpu_limits         VARCHAR(50),
    memory_limits      VARCHAR(50),
    phase              VARCHAR(50),
    restarts           INT NOT NULL DEFAULT 0,
    labels             JSONB,
    last_synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, name)
);

CREATE TABLE IF NOT EXISTS td_deployments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name       VARCHAR(200) NOT NULL,
    namespace          VARCHAR(200) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    replicas           INT NOT NULL DEFAULT 0,
    ready_replicas     INT NOT NULL DEFAULT 0,
    available_replicas INT NOT NULL DEFAULT 0,
    updated_replicas   INT NOT NULL DEFAULT 0,
    selector           JSONB,
    labels             JSONB,
    last_synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, name)
);
