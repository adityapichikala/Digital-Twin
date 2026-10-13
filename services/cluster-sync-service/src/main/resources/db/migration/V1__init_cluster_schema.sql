-- Cluster nodes snapshot
CREATE TABLE IF NOT EXISTS td_cluster_nodes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name  VARCHAR(200) NOT NULL,
    node_name     VARCHAR(200) NOT NULL,
    instance_type VARCHAR(100),
    capacity_cpu  VARCHAR(20),
    capacity_mem  VARCHAR(20),
    allocatable_cpu VARCHAR(20),
    allocatable_mem VARCHAR(20),
    status        VARCHAR(50),
    labels        JSONB,
    conditions    JSONB,
    synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, node_name)
);

-- Namespaces
CREATE TABLE IF NOT EXISTS td_namespaces (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name  VARCHAR(200) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    status        VARCHAR(50),
    labels        JSONB,
    synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, name)
);

-- Workloads (Deployments, StatefulSets, DaemonSets)
CREATE TABLE IF NOT EXISTS td_workloads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name    VARCHAR(200) NOT NULL,
    namespace       VARCHAR(200) NOT NULL,
    kind            VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    desired_replicas INT,
    ready_replicas   INT,
    resource_requests JSONB,
    resource_limits   JSONB,
    labels          JSONB,
    synced_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, kind, name)
);

CREATE INDEX idx_workloads_cluster ON td_workloads(cluster_name);
CREATE INDEX idx_workloads_ns ON td_workloads(namespace);

-- Sync log
CREATE TABLE IF NOT EXISTS td_sync_log (
    id           BIGSERIAL PRIMARY KEY,
    cluster_name VARCHAR(200) NOT NULL,
    synced_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    node_count   INT,
    workload_count INT,
    error_message TEXT
);
