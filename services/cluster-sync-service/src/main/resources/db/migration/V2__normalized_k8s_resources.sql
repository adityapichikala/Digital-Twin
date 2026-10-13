-- ==============================================================================
-- V2__normalized_k8s_resources.sql
-- Normalized live cluster snapshot tables for TwinDigital
-- Resources: Nodes, Pods, Services, Deployments, HPAs, PDBs, SyncState
-- ==============================================================================

-- 1. Nodes (Allocatable CPU/Memory, Capacity, Ready Status, Labels, Conditions)
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
CREATE INDEX IF NOT EXISTS idx_td_nodes_cluster ON td_nodes(cluster_name);
CREATE INDEX IF NOT EXISTS idx_td_nodes_ready ON td_nodes(ready);

-- 2. Pods (Namespace, Node, Owner, Requests, Limits, Phase, Restarts, Labels)
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
CREATE INDEX IF NOT EXISTS idx_td_pods_cluster_ns ON td_pods(cluster_name, namespace);
CREATE INDEX IF NOT EXISTS idx_td_pods_node ON td_pods(node_name);
CREATE INDEX IF NOT EXISTS idx_td_pods_phase ON td_pods(phase);

-- 3. Services (Selector, Ports, Service Type, ClusterIP, Labels)
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
CREATE INDEX IF NOT EXISTS idx_td_services_cluster_ns ON td_services(cluster_name, namespace);

-- 4. Deployments (Replicas, Ready/Available Replicas, Selector, Labels)
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
CREATE INDEX IF NOT EXISTS idx_td_deployments_cluster_ns ON td_deployments(cluster_name, namespace);

-- 5. HorizontalPodAutoscalers (Target, Min/Max Replicas, Current/Desired Replicas, Utilization)
CREATE TABLE IF NOT EXISTS td_hpas (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name            VARCHAR(200) NOT NULL,
    namespace               VARCHAR(200) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    target_kind             VARCHAR(100),
    target_name             VARCHAR(200),
    min_replicas            INT,
    max_replicas            INT NOT NULL DEFAULT 1,
    current_replicas        INT,
    desired_replicas        INT,
    current_cpu_utilization INT,
    target_cpu_utilization  INT,
    labels                  JSONB,
    last_synced_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, name)
);
CREATE INDEX IF NOT EXISTS idx_td_hpas_cluster_ns ON td_hpas(cluster_name, namespace);

-- 6. PodDisruptionBudgets (MinAvailable, MaxUnavailable, Healthy/Disruptions Allowed, Selector)
CREATE TABLE IF NOT EXISTS td_pdbs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name        VARCHAR(200) NOT NULL,
    namespace           VARCHAR(200) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    min_available       VARCHAR(50),
    max_unavailable     VARCHAR(50),
    current_healthy     INT,
    desired_healthy     INT,
    disruptions_allowed INT,
    expected_pods       INT,
    selector            JSONB,
    labels              JSONB,
    last_synced_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, namespace, name)
);
CREATE INDEX IF NOT EXISTS idx_td_pdbs_cluster_ns ON td_pdbs(cluster_name, namespace);

-- 7. Cluster Sync State & Heartbeat Log
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
