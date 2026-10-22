-- ==============================================================================
-- V2__pricing_and_shared_tables.sql
-- Additional instance pricing data for ap-south-1 and common EKS types.
--
-- td_nodes, td_pods, td_deployments are created and owned exclusively by
-- cluster-sync-service (flyway_schema_history_cluster_sync).
-- cost-risk-service reads them at runtime via read-only JPA mappings;
-- it must NOT create or alter them.
-- ==============================================================================

-- Additional pricing seed rows (ap-south-1 and more us-east-1)
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
  spot_price      = EXCLUDED.spot_price,
  vcpu            = EXCLUDED.vcpu,
  memory_gib      = EXCLUDED.memory_gib,
  updated_at      = NOW();
