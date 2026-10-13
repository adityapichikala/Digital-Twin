# TwinDigital Architecture Decision Records (ADRs)

This directory documents the foundational architectural and technical decisions made for the TwinDigital platform, following the [Michael Nygard ADR format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

---

## Index of Architectural Decisions

| ADR Number | Title | Status | Date | Decision Summary |
| :---: | :--- | :---: | :---: | :--- |
| **[ADR-001](./0001-terraform-vs-cloudformation.md)** | **Infrastructure as Code: Terraform vs. AWS CloudFormation** | `Accepted` | Oct 2026 | Chose **HashiCorp Terraform** with S3 remote state and DynamoDB locking over AWS CloudFormation for modular multi-cloud readiness, rich provider ecosystem, and deterministic plan/apply workflows. |
| **[ADR-002](./0002-kubernetes-watch-informers-vs-polling.md)** | **Cluster State Sync: Kubernetes Watch/Informers vs. Fixed-Interval Polling** | `Accepted` | Oct 2026 | Chose **Kubernetes SharedIndexInformer (List + Watch)** over fixed-interval polling to eliminate API server query exhaustion, achieve sub-second event propagation, and enable resilient reconnection with exponential backoff. |
| **[ADR-003](./0003-postgresql-vs-nosql-graph-databases.md)** | **Cluster Topology & Audit Storage: PostgreSQL vs. Graph/NoSQL Datastores** | `Accepted` | Oct 2026 | Chose **PostgreSQL with recursive CTEs and Flyway migrations** over Neo4j or DynamoDB to maintain strict ACID relational consistency, zero operational overhead on RDS, and performant directed acyclic graph traversals. |
| **[ADR-004](./0004-amazon-ecr-vs-docker-hub.md)** | **Container Image Registry: Amazon ECR vs. Docker Hub** | `Accepted` | Oct 2026 | Chose **Amazon ECR** over Docker Hub to leverage IAM/IRSA tokenless authentication, eliminate AWS NAT Gateway internet egress fees, prevent HPA `429 Too Many Requests` pull throttling, and enable automated CVE vulnerability scanning. |
