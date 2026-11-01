# TwinDigital — Kubernetes Operations MVP

[![CI/CD Pipeline](https://img.shields.io/badge/CI%2FCD-Jenkins%208--Stage%20Declarative-blue.svg)](./Jenkinsfile)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-v1.28%2B%20EKS%20(ap--south--1)-326CE5.svg)](./k8s)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F.svg)](./services)
[![React](https://img.shields.io/badge/React-19-61DAFB.svg)](./frontend)
[![Registry](https://img.shields.io/badge/Registry-Amazon%20ECR-FF9900.svg)](./ci/REGISTRY_RECOMMENDATION.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

TwinDigital is an MVP for exploring Kubernetes topology and proposed workload changes through a web UI, API gateway, and Java services. Its local Compose profile includes a seeded demo topology. Kubernetes, Prometheus, and AWS integrations are implemented in the codebase, but their live behavior has not been benchmarked in this project run.

## Status at a glance

- **[Verified — local run on 2026-11-01]** Login through the gateway succeeded as `ADMIN`; the authenticated topology endpoint returned the built-in demo graph.
- **[Verified — local run on 2026-11-02]** `docker compose up --build -d` brought all nine Compose services to healthy. The topology service returned `dataSource: SEED` with 18 nodes and 19 edges; simulation returned `seedSnapshotUsed: true` without a connected Kubernetes cluster.
- **[Verified — automated tests]** Gateway Jest: 17/17 passed on 2026-11-01. Java service suites: 92/92 recorded (auth 19, topology 8, simulation 25, and cost-risk 15 run on 2026-11-02; cluster-sync 25 from 2026-11-01); see [test results](./docs/testing/RESULTS_TEMPLATE.md).
- **[Verified — local checks on 2026-11-02]** Frontend lint completed with 3 warnings and no errors; TypeScript typecheck and production build passed.
- **[Implemented]** Login and role checks, Kubernetes List+Watch integration, topology and blast-radius traversal, simulation checks, cost-risk APIs, and Kubernetes deployment/monitoring manifests.
- **[Planned — not yet run]** Live-cluster latency measurement, load testing, chaos testing, and simulation accuracy validation. Results remain marked “not yet run” in the [results template](./docs/testing/RESULTS_TEMPLATE.md).

## MVP scope

`docker-compose.yml` starts PostgreSQL, Redis, cluster-sync, auth-service, topology-service, simulation, cost-risk-service, the gateway, and the frontend. The UI is served at `http://localhost:8088`; the gateway is at `http://localhost:3000`.

- **Storage:** PostgreSQL and Redis.
- **Backend:** auth-service, cluster-sync, topology-service, simulation-service, cost-risk-service, and the Express gateway.
- **Frontend:** React UI served by the frontend container.
- **Local data sources:** seeded topology when no kubeconfig is supplied; Prometheus and AWS billing are external and are not started by Compose.

Without `KUBECONFIG_BASE64`, cluster-sync has no Kubernetes cluster to read and topology serves its seeded demo graph. Compose does not start Prometheus or provision AWS credentials, so live utilization and AWS billing data are not part of the verified local flow. The load, chaos, and simulation-accuracy checks have not been run.

---

## 1. Architecture

The diagram shows the service layout and intended external integrations. The local MVP scope is listed above; live EKS, Prometheus, and AWS service connections have not been verified here.

```mermaid
flowchart TD
    subgraph ClientLayer ["Client & Ingress Layer"]
        User["Platform Engineer / SRE"]
        Ingress["AWS ALB / Nginx Ingress Controller (TLS Termination)"]
        Frontend["Frontend SPA (React 19 + Vite + ReactFlow)<br/>5-Second REST Polling (No WebSockets)"]
    end

    subgraph GatewayLayer ["Edge & Security Gateway"]
        Gateway["API Gateway (Express on Node 20)<br/>• JWT Verification & RBAC (/simulate protected)<br/>• Rate Limiting (Redis Store with In-Memory Fallback)<br/>• Request Tracing & Prom-client"]
    end

    subgraph ServiceMesh ["Five Java Microservices (Spring Boot 3.3 / Java 17)"]
        AuthSvc["auth-service (:8081)<br/>BCrypt · JWT · Roles · Refresh Tokens"]
        SyncSvc["cluster-sync-service (:8082)<br/>K8s Informers (List+Watch) · PostgreSQL Sync"]
        TopoSvc["topology-service (:8083)<br/>In-Memory BFS Blast Radius Analysis"]
        SimSvc["simulation-service (:8084)<br/>5 Check Engines · Cost Delta · Dry-Run Validation"]
        CostSvc["cost-risk-service (:8085)<br/>(Combined Cost & Risk)<br/>PromQL 7d p95 · AWS Cost Explorer"]
    end

    subgraph BackingStores ["Persistence & Cache"]
        Postgres[("Amazon RDS PostgreSQL 15 (ap-south-1)<br/>Normalized Cluster Snapshots · Simulation Audits")]
        Redis[("Amazon ElastiCache Redis<br/>Refresh Tokens · Rate-Limit Counters")]
    end

    subgraph CloudTelemetry ["Live EKS & AWS Telemetry (ap-south-1)"]
        EKS_API["EKS Control Plane<br/>(Nodes, Pods, Deployments, HPAs, PDBs)"]
        Prometheus["kube-prometheus-stack<br/>(cAdvisor, Node Exporter, Micrometer)"]
        AWS_CE["AWS Cost Explorer API<br/>(IRSA Credentials · Caffeine Cache)"]
    end

    %% Traffic flows
    User -->|HTTPS| Ingress
    Ingress -->|Static UI /| Frontend
    Ingress -->|API /api| Gateway
    Frontend -->|REST (5s Polling)| Gateway

    Gateway -->|Auth / Validate| AuthSvc
    Gateway -->|Sync Status| SyncSvc
    Gateway -->|Topology Graph| TopoSvc
    Gateway -->|Run Simulations /simulate| SimSvc
    Gateway -->|Cost & Right-Sizing| CostSvc

    SyncSvc -->|List + Watch Informers| EKS_API
    SyncSvc -->|Persist Snapshots| Postgres
    CostSvc -->|PromQL 7d p95| Prometheus
    CostSvc -->|Pricing / Billing| AWS_CE
    SimSvc -->|Graph Query| TopoSvc
    SimSvc -->|Cost Delta /api/v1/cost/estimate-change| CostSvc
    SimSvc -->|Audit Log| Postgres
    AuthSvc -->|Credentials & Roles| Postgres
    AuthSvc -->|Refresh Tokens| Redis
    Gateway -->|Rate Limit Counters| Redis
```

---

## 2. Capabilities and status

### Cluster synchronization
- **[Implemented]** [`cluster-sync-service`](./services/cluster-sync-service) uses Kubernetes List+Watch informers and persists supported cluster resources to PostgreSQL; it includes sync health and reconnect logic.
- **[Verified — local run on 2026-11-01]** Compose started without a kubeconfig. This verifies service startup, not synchronization against a live cluster or event latency.
- **[Planned]** Measure update and reconnect latency against a live Kubernetes cluster.

### Topology and blast-radius view
- **[Implemented]** [`topology-service`](./services/topology-service) builds topology from stored cluster resources and traverses dependency edges with BFS. The frontend polls the API every five seconds.
- **[Verified — local run on 2026-11-01]** The gateway returned the seeded graph with 18 nodes and 19 edges when no cluster data was configured.
- **[Planned]** Validate graph completeness and blast-radius results against a live cluster.

### What-if simulation
- **[Implemented]** [`simulation-service`](./services/simulation-service) contains checks for scheduling fit, resource limits, node removal/PDB impact, HPA impact, and blast radius. Outcomes can include cost estimates and audit records.
- **[Planned]** Compare predicted outcomes with observed Kubernetes scheduling and disruption behavior. The accuracy harness is marked “not yet run.”

Implemented checks:
  1. **SchedulingFit**: Bin-packing simulation verifying that all pods fit on worker nodes given allocatable capacity minus existing requests.
  2. **LimitSanity**: Rejects invalid configurations (`limit < request`, `request > largest node`, missing memory limits, extreme ratios).
  3. **NodeRemovalImpact**: Validates whether displaced pods can be rescheduled elsewhere while respecting PodDisruptionBudgets (PDB).
  4. **HpaImpact**: Assesses whether proposed resource changes make HPA min/max bounds feasible on current cluster headroom.
  5. **BlastRadius**: Queries the topology engine's BFS model to list all dependent services impacted by the proposed change.
- **[Implemented] Cost delta integration**: Calls `cost-risk-service` to attach estimated monthly cost deltas to simulation outcomes.
- **[Implemented] Audit logging**: Stores simulation inputs and outcomes in PostgreSQL.

### Cost and risk analysis
- **[Implemented]** [`cost-risk-service`](./services/cost-risk-service) includes Prometheus utilization queries, right-sizing recommendations, AWS Cost Explorer integration, pricing data, and Micrometer metrics.
- **[Planned]** Verify results with configured Prometheus history and AWS credentials. Compose does not provide those external systems.

### Kubernetes deployment controls
- **[Implemented in manifests]** The Kubernetes configuration includes pod security contexts, RBAC, NetworkPolicies, IRSA, and External Secrets resources.
- **[Planned]** Verify enforcement and rollout behavior in a deployed cluster. A rendered manifest is not evidence of a live security or availability test.

---

## 3. Repository Structure

```
cloudca/
├── infra/                               # Terraform: Multi-AZ VPC, EKS, RDS, ElastiCache, IAM
├── services/
├── services/                            # Five Java microservices (Spring Boot 3.3, Java 17)
│   ├── auth-service/                    # BCrypt, JWT (HS256/RS256), RBAC (ADMIN/VIEWER), Redis Refresh Tokens
│   ├── cluster-sync-service/            # Kubernetes Java Client Informers (List+Watch), Flyway DB, Heartbeats
│   ├── topology-service/                # Directed Graph Engine, In-Memory BFS Blast Radius API
│   ├── simulation-service/              # Modular 5-check What-If Engine, Cost Delta, Audit Log (/simulate)
│   └── cost-risk-service/               # Combined Cost & Risk: PromQL 7d p95 Right-Sizing, AWS Cost Explorer (ap-south-1)
├── gateway/                             # Express Gateway: Rate limiting (Redis), JWT RBAC, Prom-client
├── frontend/                            # React 19 + Vite + ReactFlow + TypeScript DAG Canvas (5s polling)
├── k8s/                                 # Production Kustomize manifests (Overlays: staging & prod)
│   ├── base/                            # Hardened Deployments, Services, HPAs, Ingress, NetworkPolicies
│   ├── terraform/                       # IRSA IAM roles & AWS Secrets Manager KMS resources
│   ├── monitoring/                      # Helm values for kube-prometheus-stack, alerts, dashboards
│   ├── SECURITY.md                      # Detailed security controls guide with verification commands
│   └── README.md                        # Deployment quickstart
├── ci/                                  # Enterprise CI/CD infrastructure
│   ├── agent/                           # Standardized Jenkins agent Dockerfile (JDK17, Maven, Node 20, Trivy, kubectl)
│   ├── docker-compose.yml               # Cheap Jenkins + SonarQube + PostgreSQL stack (~3GB RAM)
│   ├── helm-jenkins-values.yaml         # Low-cost Helm values for Jenkins on t3.medium
│   ├── helm-sonarqube-values.yaml       # Low-cost Helm values for SonarQube on t3.medium
│   ├── REGISTRY_RECOMMENDATION.md       # Amazon ECR vs. Docker Hub architectural comparison
│   └── README.md                        # Jenkins 8-stage pipeline operations & credential guide
├── docs/
│   ├── adr/                             # Architecture Decision Records (ADR-001 to ADR-004)
│   ├── testing/                         # Stress testing (k6), Chaos scripts, Simulation harness
│   │   ├── k6/                          # k6 load testing script (ramping to N req/s)
│   │   ├── chaos/                       # Bash & PowerShell chaos testing automation
│   │   ├── simulation/                  # Python simulation accuracy harness + 10 fixtures; CSV generated only after a run
│   │   ├── RESULTS_TEMPLATE.md          # Results template with detection & recovery times
│   │   └── RUNBOOK.md                   # Operational testing runbook
│   └── DEMO_SCRIPT.md                   # 3-minute executive presentation script
├── Jenkinsfile                          # 8-Stage Declarative pipeline (ZAP non-blocking, ECR, Kustomize)
└── docker-compose.yml                   # Local development orchestrator (ap-south-1 default)
```

---

## 4. CI/CD: 8-Stage Declarative Pipeline

The project implements a hardened, 8-stage declarative Jenkins pipeline ([`Jenkinsfile`](./Jenkinsfile)) running on a custom agent image ([`ci/agent/Dockerfile`](./ci/agent/Dockerfile)):

1. **Checkout & Path Filter**: Detects changed services to execute incremental builds; maps `DEPLOY_ENV` to target Kustomize overlays.
2. **Parallel Build & Unit Tests**: Compiles Java services via Maven (Surefire + JaCoCo coverage), runs Gateway unit tests (Jest + `jest-junit`), and runs Frontend static checks (`npm run lint`, `typecheck`, and `build`).
3. **SonarQube Analysis & Quality Gate**: Submits metrics to SonarQube with a strict fail-closed gate on code smells, coverage, and security hotspots.
4. **Build Docker Images**: Builds OCI container images tagged with the Git commit short SHA.
5. **Trivy Vulnerability Scan**: Scans images and strictly fails the build if any `HIGH` or `CRITICAL` CVEs are detected.
6. **Push to Amazon ECR**: Pushes verified images to Amazon Elastic Container Registry in region `ap-south-1` using ephemeral IAM credentials (no static Docker Hub credentials).
7. **OWASP ZAP Baseline DAST Scan**: Executes dynamic application security testing against staging in advisory (non-blocking) mode, archiving HTML/JSON vulnerability reports.
8. **Deploy to EKS & Rollout Status Check**: Deploys via Kustomize overlay (`kubectl apply -k k8s/overlays/${DEPLOY_ENV}`) and monitors rollout status with automatic rollback (`kubectl rollout undo`) on timeout or failure.

---

## 5. Quick Start

### Option A: Local Development (Docker Compose)
Ensure Docker Desktop (or Docker Engine) and Docker Compose are installed and running:

```bash
# 1. Configure local environment values
cp .env.example .env
# Edit .env and replace every CHANGE_ME value with a unique secret before starting Compose.

# 2. Build and start the complete local MVP stack
docker compose up --build -d

# 3. Wait until each service reports healthy
docker compose ps
```

Compose starts PostgreSQL and Redis first. `cluster-sync` initializes the shared cluster tables; topology, simulation, and cost-risk wait for it to become healthy. The gateway waits for auth and the other APIs. The frontend is served by its container at [http://localhost:8088](http://localhost:8088) (host port 8080 is used by Jenkins in the project’s local environment). Set `BOOTSTRAP_ADMIN_USERNAME` and a unique `BOOTSTRAP_ADMIN_PASSWORD` in `.env`; Compose has no committed admin password default.

Verify login through the gateway and use the returned `accessToken` for the topology request:

```bash
# Login; copy the accessToken value from the JSON response.
curl -sS -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${BOOTSTRAP_ADMIN_USERNAME:-admin}\",\"password\":\"${BOOTSTRAP_ADMIN_PASSWORD}\"}"

# Replace <accessToken> with that value.
curl -sS http://localhost:3000/api/v1/topology \
  -H "Authorization: Bearer <accessToken>"
```

The cost-risk API accepts `PROMETHEUS_URL` when a Prometheus server is available. Without it, the local stack still starts, but live utilization data requires separately running Prometheus. Use `docker compose logs -f` to inspect service startup and `docker compose down` to stop the stack.

Without `KUBECONFIG_BASE64`, cluster sync cannot connect to a Kubernetes cluster and topology returns the built-in demo graph. Set `KUBECONFIG_BASE64` in `.env` to a base64-encoded kubeconfig to sync a real cluster. This local Compose path is an MVP demo environment; it does not provision EKS, Prometheus, or AWS Cost Explorer credentials.

### Option B: Deploy to Amazon EKS (Kustomize)
Ensure `kubectl` is authenticated to your target EKS cluster:

```bash
# 1. Preview rendered production manifests
kubectl kustomize k8s/overlays/staging

# 2. Apply to EKS cluster
kubectl apply -k k8s/overlays/staging

# 3. Verify deployments, services, HPAs, and ingress
kubectl get pods,svc,hpa,ingress -n twindigital
```

---

## 6. Comprehensive Test Results & Validation

The TwinDigital platform incorporates automated test suites, load testing scripts, chaos engineering scenarios, and digital twin prediction accuracy validation:

### 1. Test Suite Coverage Summary (Measured)
* **Express Gateway Unit & Resilience Tests**: **17 / 17 Jest tests pass cleanly (100%)**, verifying JWT authentication, revoked token rejection (401), forged token rejection (401), role-based access control (`VIEWER` $\rightarrow$ 403 on `/simulate` routes, `ADMIN` allowed), proxy route preservation and path rewriting, `ResilientStore` rate limiting with in-memory fallback on Redis outage (`passOnStoreError: true`), and Prometheus metric exports.
* **Frontend SPA Static Analysis**: **TypeScript compilation (0 errors)** and **oxlint (0 errors, 3 warnings)** verified cleanly.
* **Java Microservices Unit & BDD Test Suites**: **92 / 92 tests passed** across auth (19), cluster-sync (25), topology (8), simulation (25), and cost-risk (15). Auth, topology, simulation, and cost-risk were run on 2026-11-02; cluster-sync was last run on 2026-11-01.
  * **auth-service**: **19 / 19 passed (100%)** (`AuthServiceTest`, `JwtTokenProviderTest`, `AuthServiceApplicationTest`, `AdminUserBootstrapTest`)
  * **cluster-sync-service**: **25 / 25 passed (100%)** (`SyncControllerTest`, `ClusterSyncMetricsTest`, `KubernetesResourceMapperTest`, `KubernetesResourcePersisterTest`, `SyncHealthTrackerTest`)
  * **topology-service**: **8 / 8 passed (100%)** (`TopologyControllerTest`, `TopologyGraphServiceTest`)
  * **simulation-service**: **25 / 25 passed (100%)** (`SimulationChecksUnitTest`, `SimulationControllerTest`, `CostOptimizationServiceTest`, `SimulationBddScenariosTest`)
  * **cost-risk-service**: **15 / 15 passed (100%)** (`AwsCostExplorerClientTest`, `PrometheusClientTest`, `CostRiskControllerTest`, `CostRiskServiceTest`)

### 2. k6 Load Testing Performance Benchmark
*Script defined in [`k6-gateway-stress.js`](./docs/testing/k6/k6-gateway-stress.js) targeting 1000 req/s against the API Gateway:*
* **Target Throughput**: $1,000.0\text{ req/s}$
* **Target p95 Latency SLA**: $< 300\text{ ms}$ — **not yet run** (pending deployed load environment)
* **Target p99 Latency SLA**: $< 800\text{ ms}$ — **not yet run**
* **Target HTTP Error Rate SLA**: $< 1.0\%$ — **not yet run**

### 3. Chaos Engineering Verification
*Automation script defined in [`chaos-scenarios.sh`](./docs/testing/chaos/chaos-scenarios.sh) requiring real assertions:*

| Chaos Scenario | Injected Fault | Expected System Reaction | Measured Recovery Time | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Pod CrashLoop** | Failing container entrypoint | Restart count reached $\ge 3$; trigger Alertmanager alert `PodFrequentRestarts` | *not yet run* | **not yet run** |
| **Worker Node Drain** | Graceful node drain & cordon | Pods evicted respecting PDBs; rescheduled on alternate nodes | *not yet run* | **not yet run** |
| **Memory Stress Pod** | 256M allocation on 128M limit | Linux cgroup OOM-killed rogue pod; neighbor pods untouched | *not yet run* | **not yet run** |
| **Service Pod Kill** | Concurrent pod termination | Record replacement readiness, request availability, and recovery time | *not yet run* | **not yet run** |
| **Redis Outage** | Scaled Redis deployment to 0 | Check whether gateway uses its in-memory rate-limit fallback and record request outcomes | *not yet run* (gateway fallback behavior covered by 17/17 Jest tests) | **not yet run** |
| **K8s API Isolation** | Blocked sync service API egress | Watch dropped; status transitioned to `STALE`; auto-reconnected | *not yet run* | **not yet run** |

### 4. Simulation Accuracy Validation
*Harness defined in [`simulation-accuracy-harness.py`](./docs/testing/simulation/simulation-accuracy-harness.py) observing actual kind cluster outcomes:*
* **Total Scenarios Configured**: 10 fixture changes ([`simulation_scenarios.json`](./docs/testing/simulation/fixtures/simulation_scenarios.json))
* **Cluster Observation Method**: Live Kubernetes API (pod phase, admission webhook errors, PDB eviction responses)
* **Live Cluster Benchmark**: **not yet run** (requires live kind/EKS cluster and running simulation-service)
* **Offline Execution**: Fails loudly if cluster or simulation-service is unreachable (no synthetic fallbacks)

---

## 7. Architecture Decision Records (ADRs)

Key architectural decisions are documented under [`docs/adr/`](./docs/adr/):

* **[ADR-001: Terraform vs. AWS CloudFormation](./docs/adr/0001-terraform-vs-cloudformation.md)**: Selected HashiCorp Terraform with S3 remote state and DynamoDB locking for unified multi-provider orchestration and deterministic execution plans.
* **[ADR-002: Kubernetes Watch/Informers vs. Polling](./docs/adr/0002-kubernetes-watch-informers-vs-polling.md)**: Selected `SharedIndexInformer` (List + Watch) to receive Kubernetes change events without fixed-interval list requests; event latency has not been measured here.
* **[ADR-003: PostgreSQL vs. NoSQL / Graph Databases](./docs/adr/0003-postgresql-vs-nosql-graph-databases.md)**: Selected PostgreSQL with in-memory BFS graph traversals for full ACID guarantees, fast dependency resolution, and versioned Flyway migrations.
* **[ADR-004: Amazon ECR vs. Docker Hub](./docs/adr/0004-amazon-ecr-vs-docker-hub.md)**: Selected Amazon ECR over Docker Hub to leverage IAM/IRSA tokenless authentication, eliminate AWS NAT Gateway internet egress fees, and avoid pull rate limits in `ap-south-1`.

---

## 8. Limitations & Future Roadmap

### Current Limitations:
1. **Single-Cluster Boundary**: Currently synchronizes and simulates changes within a single EKS cluster per deployment instance.
2. **Prometheus Retention Dependency**: 7-day p95 right-sizing calculations rely on Prometheus metric retention; in short-retention dev clusters, it falls back to instantaneous rates.
3. **Synthetic Network Traffic**: Service-to-service dependencies are inferred from Ingress definitions and Kubernetes annotations rather than deep packet inspection.

### Strategic Roadmap:
* [ ] **Multi-Cluster Fleet Federation**: Expand topology and simulation engine to model multi-region and multi-cluster EKS fleets.
* [ ] **eBPF-Powered Live Service Mesh Tracing**: Integrate with Cilium/Tetragon eBPF kernel probes to dynamically auto-discover runtime network dependencies without annotations.
* [ ] **Automated GitOps Pull Request Generation**: One-click "Apply Right-Sizing" button that automatically creates an approved GitHub pull request in the application repo updating Helm/Kustomize values.
* [ ] **Karpenter Just-in-Time Provisioning Simulation**: Model Karpenter disruption and consolidation decisions directly in the what-if simulation engine.

---

## 9. License

This project is licensed under the Apache 2.0 License. See [LICENSE](./LICENSE) for details.
