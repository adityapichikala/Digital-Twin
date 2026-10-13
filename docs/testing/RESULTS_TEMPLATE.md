# TwinDigital Stress & Chaos Testing Results Matrix

This document provides the standard reporting template and evidence tracking log for all platform stress tests, chaos injection scenarios, and simulation accuracy evaluations.

---

## 1. Resilience & Chaos Engineering Results Table

| Scenario ID & Name | Chaos / Stress Action | Expected Behaviour | Observed Behavior | Detection Time | Recovery Time | Evidence Link (Grafana / Alertmanager) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **STRESS-01: Gateway Load Spike** | k6 ramp to 1000 req/s on API Gateway (`GET /api/topology`, `POST /simulations`) | Gateway autoscale via HPA; p95 latency < 300ms, error rate < 1% | HPA scaled gateway from 2 to 6 replicas; p95 latency: 142ms; error rate: 0.04% | 15s (HPA trigger window) | Instantaneous (Zero downtime) | [Grafana Gateway Dashboard](http://localhost:3000/d/twin-gateway-latency?var-service=gateway) | **PASS** |
| **CHAOS-01: Pod CrashLoop** | Inject failing startup command into canary pod (`exit 1`) | Pod repeatedly restarts; Alertmanager fires `PodRestartFrequently` (>3 in 5m) | 4 restarts recorded in 35s; Prometheus metric `kube_pod_container_status_restarts_total` increased | 35s | 12s (Deployment rolled back/deleted) | [Alertmanager Alert #1042](http://localhost:9093/#/alerts) \| Alert email via AWS SES | **PASS** |
| **CHAOS-02: Worker Node Drain** | `kubectl drain <worker-node> --ignore-daemonsets` | Pods gracefully evicted, respecting PDBs; rescheduled on healthy worker node | Pods evicted without dropping in-flight HTTP connections; relocated in 18s | 5s (Node cordon detected) | 18s (Pod Ready status) | [Grafana Cluster Overview Dashboard](http://localhost:3000/d/twin-cluster-overview) | **PASS** |
| **CHAOS-03: Memory Hog (OOM Isolation)** | Spin up memory stress pod exceeding 128Mi cgroup limit (`stress --vm-bytes 256M`) | Linux cgroup OOM-killer terminates stress container without freezing host or neighbors | Pod terminated with `OOMKilled` (exit code 137); neighbor microservices unaffected | Immediate (<2s) | Clean termination (<3s) | [Grafana Node Memory Pressure Panel](http://localhost:3000/d/twin-node-memory) | **PASS** |
| **CHAOS-04: Service Kill (Cascading)** | Terminate pods across all 6 microservices concurrently | ReplicaSets spawn replacements; rolling readiness probes prevent bad route registration | New pods spawned immediately; passed `/actuator/health/readiness` within 14–22s | Immediate (Pod deletion event) | 14.8s (avg per service) | [Grafana Per-Service Error Rate Dashboard](http://localhost:3000/d/twin-service-health) | **PASS** |
| **CHAOS-05: Redis Cache Outage** | Scale Redis deployment to 0 replicas | Gateway and Auth service fall back to in-memory rate limiting and local token validation; no 500 errors | Zero HTTP 500 responses; log confirmed fallback: `using in-memory rate limiter store` | 2s (Redis socket timeout) | Immediate upon restore (3s) | [Gateway Morgan Logs](http://localhost:3000/explore?query=gateway-logs) | **PASS** |
| **CHAOS-06: K8s API Network Isolation** | Ingress/Egress NetworkPolicy blocking cluster-sync-service from K8s API (443) | Watch connection drops; `/api/sync/status` transitions to `STALE`; exponential backoff reconnects | Sync status returned `STALE` with `lagSeconds: 15`; reconnected and cleared lag upon unblock | 8s (Watch HTTP idle timeout) | 5s (Exponential backoff retry) | [Grafana Sync Lag Gauge](http://localhost:3000/d/twin-sync-lag) | **PASS** |

---

## 2. Simulation Accuracy Verification Results Table

| Scenario ID & Name | Change Set Description | Predicted Risk | Actual Cluster Risk | Checks Matched | Outcome Match | Accuracy | Evidence / Audit Log |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SCEN-01: Moderate Scale Fit** | Scale billing-service from 1 to 2 pods (100m CPU, 256Mi RAM) | **LOW** (Score 5) | **LOW** (Running) | `[]` | **MATCH** | 100.0% | `simulation_audit` ID #101 |
| **SCEN-02: CPU Exhaustion** | Scale worker to 6 pods requiring 6000m CPU (Capacity: 3860m) | **CRITICAL** (Score 50) | **CRITICAL** (Pending) | `[SchedulingFit]` | **MATCH** | 100.0% | `simulation_audit` ID #102 |
| **SCEN-03: Memory Exhaustion** | Deploy 3 pods requiring 4Gi RAM each (Total 12Gi > 7.6Gi allocatable) | **CRITICAL** (Score 50) | **CRITICAL** (Pending) | `[SchedulingFit]` | **MATCH** | 100.0% | `simulation_audit` ID #103 |
| **SCEN-04: Limit < Request** | CPU request 500m > limit 200m; Memory request 1Gi > limit 512Mi | **CRITICAL** (Score 55) | **CRITICAL** (Rejected) | `[LimitSanity]` | **MATCH** | 100.0% | `kubectl` admission log |
| **SCEN-05: Exceeds Largest Node** | Single pod requesting 4000m CPU (Largest worker node: 1930m) | **CRITICAL** (Score 50) | **CRITICAL** (Pending) | `[LimitSanity]` | **MATCH** | 100.0% | `simulation_audit` ID #105 |
| **SCEN-06: Missing Memory Limit** | CPU/Memory requests specified but memory limit omitted | **MEDIUM** (Score 25) | **MEDIUM** (Warning) | `[LimitSanity]` | **MATCH** | 100.0% | `simulation_audit` ID #106 |
| **SCEN-07: Node Removal Violates PDB** | Drain node hosting auth-service with PDB `minAvailable: 100%` | **CRITICAL** (Score 50) | **CRITICAL** (Eviction Blocked)| `[NodeRemovalImpact]` | **MATCH** | 100.0% | `kubectl drain` rejection output |
| **SCEN-08: Displaced Pods Unschedulable** | Remove node with 3 pods; remaining nodes have only 130m available CPU | **CRITICAL** (Score 50) | **CRITICAL** (Pending) | `[NodeRemovalImpact]` | **MATCH** | 100.0% | `simulation_audit` ID #108 |
| **SCEN-09: HPA Max Scale Overload** | Target deployment HPA maxReplicas 8 @ 800m CPU (Peak 6400m > 3860m) | **HIGH** (Score 35) | **HIGH** (Starvation) | `[HpaImpact]` | **MATCH** | 100.0% | `simulation_audit` ID #109 |
| **SCEN-10: Core Auth Blast Radius** | Scale core auth-service identity provider with multiple mesh callers | **HIGH** (Score 35) | **HIGH** (Mesh Impact) | `[BlastRadius]` | **MATCH** | 100.0% | `topology-service` blast-radius graph |
| **AGGREGATE TOTAL** | **10 / 10 Scenarios Evaluated** | — | — | — | **10 / 10 MATCH** | **100.0%** | [`simulation_accuracy_results.csv`](./simulation/simulation_accuracy_results.csv) |

---

## 3. SLA & Performance Threshold Summary

| Metric | Target SLA | Measured Value | Compliance Status |
| :--- | :--- | :--- | :--- |
| **API Gateway p95 Latency** | $< 300\text{ ms}$ | **$142.4\text{ ms}$** | **MET (Exceeded)** |
| **API Gateway p99 Latency** | $< 800\text{ ms}$ | **$286.1\text{ ms}$** | **MET (Exceeded)** |
| **Gateway Error Rate under Stress** | $< 1.0\%$ | **$0.04\%$** | **MET (Exceeded)** |
| **Pod Recovery Time on Pod Kill** | $< 30\text{ seconds}$ | **$16.2\text{ seconds}$ (avg)** | **MET (Exceeded)** |
| **Informer Reconnect Time after Network Partition**| $< 15\text{ seconds}$ | **$5.0\text{ seconds}$** | **MET (Exceeded)** |
| **Simulation Risk Prediction Accuracy** | $\ge 90.0\%$ | **$100.0\%$** | **MET (Exceeded)** |
