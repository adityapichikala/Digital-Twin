-- Topology nodes (mirrors td_cluster_nodes, enriched with graph layout)
CREATE TABLE IF NOT EXISTS td_topology_nodes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name  VARCHAR(200) NOT NULL,
    node_type     VARCHAR(50)  NOT NULL, -- NODE, NAMESPACE, WORKLOAD, POD
    external_id   VARCHAR(500) NOT NULL, -- qualified name
    label         VARCHAR(500),
    metadata      JSONB,
    pos_x         DOUBLE PRECISION DEFAULT 0,
    pos_y         DOUBLE PRECISION DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cluster_name, node_type, external_id)
);

-- Topology edges
CREATE TABLE IF NOT EXISTS td_topology_edges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_name  VARCHAR(200) NOT NULL,
    source_id     UUID NOT NULL REFERENCES td_topology_nodes(id) ON DELETE CASCADE,
    target_id     UUID NOT NULL REFERENCES td_topology_nodes(id) ON DELETE CASCADE,
    edge_type     VARCHAR(50),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_topo_nodes_cluster ON td_topology_nodes(cluster_name);
CREATE INDEX idx_topo_edges_source  ON td_topology_edges(source_id);
CREATE INDEX idx_topo_edges_target  ON td_topology_edges(target_id);
