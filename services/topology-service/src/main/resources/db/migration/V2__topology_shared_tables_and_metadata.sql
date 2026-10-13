-- ==============================================================================
-- V2__topology_shared_tables_and_metadata.sql
-- Ensure cluster sync snapshot tables are defined and add service dependencies
-- ==============================================================================

-- 1. Nodes (if not already created by cluster-sync-service)
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

-- 2. Pods
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

-- 3. Deployments
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

-- 4. Services
CREATE TABLE IF NOT EXISTS td_services (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name       VARCHAR(200) NOT NULL,
    namespace          VARCHAR(200) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    service_type       VARCHAR(50),
    cluster_ip         VARCHAR(50),
    selector           JSONB,
    ports              JSONB,
    labels             JSONB,
    last_synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, name)
);

-- 5. Sync State
CREATE TABLE IF NOT EXISTS td_sync_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name    VARCHAR(200) NOT NULL UNIQUE,
    status          VARCHAR(50) NOT NULL DEFAULT 'HEALTHY',
    connected       BOOLEAN NOT NULL DEFAULT true,
    lag_seconds     BIGINT NOT NULL DEFAULT 0,
    reconnect_count INT NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resource_counts JSONB,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Service-to-Service Inferred Dependencies Table
CREATE TABLE IF NOT EXISTS td_service_dependencies (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name  VARCHAR(200) NOT NULL,
    source_service VARCHAR(200) NOT NULL,
    target_service VARCHAR(200) NOT NULL,
    source_type   VARCHAR(50)  NOT NULL DEFAULT 'ANNOTATION', -- 'ANNOTATION', 'INGRESS', 'ENV'
    description   TEXT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, source_service, target_service)
);

CREATE INDEX IF NOT EXISTS idx_svc_dep_source ON td_service_dependencies(source_service);
CREATE INDEX IF NOT EXISTS idx_svc_dep_target ON td_service_dependencies(target_service);
