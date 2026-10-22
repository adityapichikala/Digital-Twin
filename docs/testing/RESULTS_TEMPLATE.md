# TwinDigital Stress & Chaos Testing Results Matrix

This document provides the standard reporting template and evidence tracking log for all platform stress tests, chaos injection scenarios, and simulation accuracy evaluations.

> [!NOTE]
> All live cluster benchmarks, chaos injection experiments, and simulation accuracy runs below reflect only verified, measured numbers. Any benchmark or scenario that has not yet been executed against a live test cluster is explicitly marked as **not yet run**.

---

## 1. Resilience & Chaos Engineering Results Table

*Automation suite implemented in [`docs/testing/chaos/chaos-scenarios.sh`](./chaos/chaos-scenarios.sh). Live cluster execution requires kind/EKS test cluster.*

| Scenario ID & Name | Chaos / Stress Action | Expected Behaviour | Observed Behavior | Detection Time | Recovery Time | Evidence Link (Grafana / Alertmanager) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **STRESS-01: Gateway Load Spike** | k6 ramp to 1000 req/s on API Gateway (`GET /api/topology`, `POST /simulate`) | Gateway autoscale via HPA; p95 latency < 300ms, error rate < 1% | not yet run | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-01: Pod CrashLoop** | Inject failing startup command into canary pod (`exit 1`) | Pod repeatedly restarts; Alertmanager fires `PodFrequentRestarts` (>3 in 5m) | not yet run | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-02: Worker Node Drain** | `kubectl drain <worker-node> --ignore-daemonsets` | Pods gracefully evicted, respecting PDBs; rescheduled on healthy worker node | not yet run | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-03: Memory Hog (OOM Isolation)** | Spin up memory stress pod exceeding 128Mi cgroup limit (`stress --vm-bytes 256M`) | Linux cgroup OOM-killer terminates stress container without freezing host or neighbors | not yet run | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-04: Service Kill (Cascading)** | Terminate pods across all 6 microservices concurrently | ReplicaSets spawn replacements; rolling readiness probes prevent bad route registration | not yet run | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-05: Redis Cache Outage** | Scale Redis deployment to 0 replicas | Gateway falls back to in-memory rate limiting with `passOnStoreError: true`; zero 500 errors | not yet run (Unit test verified in Jest) | not yet run | not yet run | not yet run | not yet run |
| **CHAOS-06: K8s API Network Isolation** | Ingress/Egress NetworkPolicy blocking cluster-sync-service from K8s API (443) | Watch connection drops; `/api/sync/status` transitions to `STALE`; exponential backoff reconnects | not yet run | not yet run | not yet run | not yet run | not yet run |

---

## 2. Simulation Accuracy Verification Results Table

*Validation harness implemented in [`docs/testing/simulation/simulation-accuracy-harness.py`](./simulation/simulation-accuracy-harness.py). Live cluster observation requires a running kind/EKS cluster and live simulation-service.*

| Scenario ID & Name | Change Set Description | Predicted Risk | Actual Cluster Risk | Checks Matched | Outcome Match | Accuracy | Evidence / Audit Log |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SCEN-01: Moderate Scale Fit** | Scale billing-service from 1 to 2 pods (100m CPU, 256Mi RAM) | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-02: CPU Exhaustion** | Scale worker to 6 pods requiring 6000m CPU (Capacity: 3860m) | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-03: Memory Exhaustion** | Deploy 3 pods requiring 4Gi RAM each (Total 12Gi > 7.6Gi allocatable) | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-04: Limit < Request** | CPU request 500m > limit 200m; Memory request 1Gi > limit 512Mi | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-05: Exceeds Largest Node** | Single pod requesting 4000m CPU (Largest worker node: 1930m) | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-06: Missing Memory Limit** | CPU/Memory requests specified but memory limit omitted | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-07: Node Removal Violates PDB** | Drain node hosting auth-service with PDB `minAvailable: 100%` | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-08: Displaced Pods Unschedulable** | Remove node with 3 pods; remaining nodes have only 130m available CPU | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-09: HPA Max Scale Overload** | Target deployment HPA maxReplicas 8 @ 800m CPU (Peak 6400m > 3860m) | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **SCEN-10: Core Auth Blast Radius** | Scale core auth-service identity provider with multiple mesh callers | not yet run | not yet run | not yet run | not yet run | not yet run | not yet run |
| **AGGREGATE TOTAL** | **10 Scenarios Registered in Fixtures** | — | — | — | — | **not yet run** | Live harness pending cluster |

---

## 3. SLA & Performance Threshold Summary

| Metric | Target SLA | Measured Value | Compliance Status |
| :--- | :--- | :--- | :--- |
| **API Gateway Unit Tests** | 100% passing | **13 / 13 passed (100%)** | **MET** |
| **Frontend TypeScript & Lint** | 0 errors | **0 errors, 3 warnings** | **MET** |
| **API Gateway p95 Latency** | $< 300\text{ ms}$ | *not yet run* | not yet run |
| **API Gateway p99 Latency** | $< 800\text{ ms}$ | *not yet run* | not yet run |
| **Gateway Error Rate under Stress** | $< 1.0\%$ | *not yet run* | not yet run |
| **Pod Recovery Time on Pod Kill** | $< 30\text{ seconds}$ | *not yet run* | not yet run |
| **Informer Reconnect Time after Network Partition**| $< 15\text{ seconds}$ | *not yet run* | not yet run |
| **Simulation Risk Prediction Accuracy** | $\ge 90.0\%$ | *not yet run* | not yet run |
