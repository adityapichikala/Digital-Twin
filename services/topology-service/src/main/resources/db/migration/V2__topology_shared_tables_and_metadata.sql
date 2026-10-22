-- ==============================================================================
-- V2__topology_shared_tables_and_metadata.sql
-- Topology-service-owned tables only.
--
-- td_nodes, td_pods, td_services, td_deployments, td_hpas, td_pdbs, td_sync_state
-- are created and owned exclusively by cluster-sync-service (flyway_schema_history_cluster_sync).
-- topology-service reads them at runtime; it must NOT create or alter them.
-- ==============================================================================

-- Service-to-Service inferred dependencies (topology-owned)
CREATE TABLE IF NOT EXISTS td_service_dependencies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name   VARCHAR(200) NOT NULL,
    source_service VARCHAR(200) NOT NULL,
    target_service VARCHAR(200) NOT NULL,
    source_type    VARCHAR(50)  NOT NULL DEFAULT 'ANNOTATION', -- 'ANNOTATION', 'INGRESS', 'ENV'
    description    TEXT,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, source_service, target_service)
);

CREATE INDEX IF NOT EXISTS idx_svc_dep_source ON td_service_dependencies(source_service);
CREATE INDEX IF NOT EXISTS idx_svc_dep_target ON td_service_dependencies(target_service);
