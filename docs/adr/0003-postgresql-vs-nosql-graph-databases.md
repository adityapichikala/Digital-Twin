# ADR-003: Storage Architecture — PostgreSQL vs. NoSQL / Graph Databases

* **Status**: `Accepted`
* **Date**: October 2026
* **Deciders**: Database Architecture Team, Platform Engineering

---

## 1. Context and Problem Statement

TwinDigital requires persistent storage for three distinct data domains:
1. **Cluster Topology Snapshots**: Structured representations of Nodes, Pods, Deployments, Services, HPAs, and PDBs synced from Kubernetes.
2. **Directed Graph Relationships**: Service dependencies (`Node -> Pod -> Deployment -> Service` and `twindigital.io/depends-on` edges).
3. **Transactional Audit Logs & User Auth**: BCrypt user credentials, role assignments, pricing catalogs, and immutable `simulation_audit` event logs.

We evaluated whether to use a dedicated graph database (Neo4j / Amazon Neptune), a NoSQL document database (DynamoDB / MongoDB), or a relational database with graph traversal capabilities (PostgreSQL).

---

## 2. Decision Drivers

* **ACID Transactions**: Financial cost evaluations, simulation audits, and cluster state mutations require strict transaction boundaries and relational integrity.
* **Graph Traversal Capabilities**: The system must compute dependency chains and blast radii (upstream and downstream affected services).
* **Operational Simplicity & Cost**: TwinDigital must run economically in both development environments (single Docker container) and production (managed Amazon RDS).
* **Schema Evolution**: Database schemas must be version-controlled, auditable, and managed via automated CI/CD migrations.

---

## 3. Considered Options

* **Option 1**: PostgreSQL 15 (Relational RDS with recursive CTEs and Flyway migrations).
* **Option 2**: Dedicated Graph Database (Neo4j or Amazon Neptune).
* **Option 3**: NoSQL Document Database (MongoDB or Amazon DynamoDB).

---

## 4. Decision Outcome

**Chosen Option: Option 1 — PostgreSQL 15 with Flyway Migrations**

We selected **PostgreSQL** as the primary datastore across all TwinDigital microservices.

### Comparison Matrix

| Evaluation Dimension | PostgreSQL 15 (Selected) | Dedicated Graph DB (Neo4j / Neptune) | NoSQL (DynamoDB / MongoDB) |
| :--- | :--- | :--- | :--- |
| **ACID Integrity** | **Full ACID**: Foreign keys, unique constraints, and row-level locks. | Variable ACID; complex distributed transactions. | Eventual consistency; lacks multi-table relational guarantees. |
| **Graph Traversal** | **Recursive CTEs (`WITH RECURSIVE`)**: Traverses dependency trees in single-digit milliseconds. | Native graph traversal (Cypher / Gremlin). | Poor; requires multiple round-trips and client-side graph assembly. |
| **Operational Cost** | **Very Low**: Runs locally on ~50MB RAM (`postgres:alpine`) and on RDS `db.t4g.micro` (~$15/mo). | **High**: Amazon Neptune minimum cluster cost is ~$120–$200/month. | Moderate; requires high read/write provisioning for ad-hoc queries. |
| **Schema Governance**| **Flyway Versioned Migrations**: Deterministic schema diffs (`V1__`, `V2__`) committed in Git. | Custom schema management scripts. | Schemaless drift; validation must be enforced in application code. |
| **JSON Support** | **JSONB Columns**: Indexes arbitrary Kubernetes labels, annotations, and change payloads. | Limited property map indexing. | Native JSON documents. |

---

## 5. Architectural Implementation Details

### Recursive CTE for Blast Radius Calculations
In [`topology-service`](../../services/topology-service), dependency resolution is executed directly in PostgreSQL using recursive Common Table Expressions:

```sql
WITH RECURSIVE blast_radius AS (
    -- Anchor: direct target service
    SELECT target_service, dependent_service, 1 as depth
    FROM td_service_dependencies
    WHERE target_service = :target
    UNION ALL
    -- Recursive member: downstream cascading dependents
    SELECT d.target_service, d.dependent_service, b.depth + 1
    FROM td_service_dependencies d
    INNER JOIN blast_radius b ON d.target_service = b.dependent_service
    WHERE b.depth < 10
)
SELECT DISTINCT dependent_service FROM blast_radius;
```

---

## 6. Consequences

### Positive:
* Single unified datastore across all services, avoiding distributed multi-database synchronization overhead.
* Local developer experience is fast: `docker-compose up -d postgres` starts the entire datastore in 2 seconds.
* Flyway migrations guarantee deterministic database upgrades in the CI/CD pipeline.

### Negative / Trade-offs:
* For hyperscale graphs exceeding millions of nodes, specialized graph query engines would provide optimized graph storage. However, typical Kubernetes microservice topologies (<10,000 nodes/pods) execute in under 5ms in PostgreSQL.
