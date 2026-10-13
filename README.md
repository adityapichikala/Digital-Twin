# TwinDigital — Cloud-Native Digital Twin Platform for Kubernetes (EKS)

TwinDigital mirrors a live Amazon EKS cluster's topology and telemetry, lets engineers simulate
what-if changes (resource requests, replica counts, node removal) and returns an **explainable
risk and cost-impact result** *before* anything is applied to the real cluster.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              React + Vite Frontend                          │
│                         (ReactFlow topology canvas)                         │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │ HTTPS / WebSocket
┌─────────────────────────▼───────────────────────────────────────────────────┐
│              API Gateway  (Node.js 20 + Express)                            │
│              JWT verify · Rate-limit · Route · /health · /metrics           │
└───┬─────────────┬────────────┬──────────────┬───────────────────────────────┘
    │             │            │              │
┌───▼──┐  ┌──────▼───┐  ┌─────▼──────┐  ┌───▼────────┐  ┌─────────────────┐
│auth  │  │cluster-  │  │topology-   │  │simulation- │  │cost-risk-       │
│svc   │  │sync-svc  │  │svc         │  │svc         │  │svc              │
│:8081 │  │:8082     │  │:8083       │  │:8084       │  │:8085            │
│      │  │          │  │            │  │            │  │                 │
│JWT   │  │EKS API→  │  │Graph model │  │Diff engine │  │Spot/OD pricing  │
│RBAC  │  │Postgres  │  │PostgreSQL  │  │Redis cache │  │Risk score       │
└──┬───┘  └──────────┘  └────────────┘  └────────────┘  └─────────────────┘
   │
┌──▼──────────────────────────────────────────┐
│  PostgreSQL (RDS)  |  Redis (ElastiCache)   │
└─────────────────────────────────────────────┘
```

## Repo Layout

```
.
├── infra/              # Terraform: VPC, EKS, RDS, ElastiCache, S3 backend
├── services/
│   ├── auth-service/          # Spring Boot 3 — JWT issue/validate, user RBAC
│   ├── cluster-sync-service/  # Spring Boot 3 — EKS watcher → Postgres
│   ├── topology-service/      # Spring Boot 3 — graph read API
│   ├── simulation-service/    # Spring Boot 3 — what-if simulation engine
│   └── cost-risk-service/     # Spring Boot 3 — cost delta + risk score
├── gateway/            # Node.js 20 + Express API gateway
├── frontend/           # React + Vite + ReactFlow
├── k8s/                # Kubernetes manifests (Deployments, Services, HPA, NP)
├── ci/                 # Jenkinsfile + shared pipeline steps
└── docs/               # Architecture diagrams, ADRs
```

## Quick Start (local dev with Docker Compose)

```bash
# 1. Copy environment template
cp .env.example .env   # fill in secrets

# 2. Start backing services
docker compose up -d postgres redis

# 3. Start all Java services
docker compose up -d auth-service cluster-sync-service topology-service \
                       simulation-service cost-risk-service

# 4. Start API gateway
docker compose up -d gateway

# 5. Start frontend dev server
cd frontend && npm install && npm run dev
```

## Running Tests

```bash
# All Java services (from repo root)
mvn -pl services/auth-service,services/cluster-sync-service,\
services/topology-service,services/simulation-service,\
services/cost-risk-service test

# Gateway
cd gateway && npm test

# Frontend
cd frontend && npm test
```

## Security Notes

* All secrets are stored in AWS Secrets Manager; the External Secrets Operator
  syncs them into Kubernetes Secrets.
* No secrets ever appear in source code or Docker images.
* All pods run as non-root, with read-only root filesystems where possible.
* NetworkPolicies use a default-deny posture with explicit allow rules.
* IRSA gives each service pod the minimum required IAM permissions.
