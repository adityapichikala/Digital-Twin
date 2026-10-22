# TwinDigital Platform Stress, Chaos & Simulation Verification Runbook

This runbook provides complete operational procedures to validate that the TwinDigital platform operates reliably under heavy traffic, sustains severe infrastructure failures, and produces accurate digital twin simulation predictions.

---

## 1. Safety & Execution Guardrails

> [!CAUTION]
> **Namespace Isolation & Safety Rules**:
> 1. All chaos experiments and ephemeral test workloads **MUST** run inside dedicated namespaces (`twindigital-chaos` or `twindigital-sim-harness`).
> 2. Never run destructive node drain commands against single-node development clusters without the `--dry-run` or cordon safety check built into the scripts.
> 3. All automation scripts contain idempotent trap handlers (`cleanup()`) that uncordon nodes, remove network policies, and restore replicas upon exit or interruption.

---

## 2. Test Execution Quick Reference

```bash
# 1. Run k6 Load Test against Gateway (ramp to 500 req/s)
k6 run docs/testing/k6/k6-gateway-stress.js \
  -e GATEWAY_URL=http://localhost:8080 \
  -e TARGET_RPS=500

# 2. Run Chaos Engineering Suite (Bash / Linux)
chmod +x docs/testing/chaos/chaos-scenarios.sh
./docs/testing/chaos/chaos-scenarios.sh

# Or run Chaos Engineering Suite on Windows PowerShell:
.\docs\testing\chaos\chaos-scenarios.ps1 -TargetNamespace twindigital

# 3. Run Simulation Accuracy Verification Harness
python docs/testing/simulation/simulation-accuracy-harness.py
```

---

## 3. Procedure 1: Gateway Stress Testing (k6)

### Purpose
Proves the Express API Gateway, connection pooling, and downstream Spring Boot microservices maintain low latency and zero errors under sustained traffic and traffic spikes.

### Execution Command
```bash
k6 run docs/testing/k6/k6-gateway-stress.js \
  -e GATEWAY_URL="http://twindigital.io" \
  -e TARGET_RPS=1000 \
  -e JWT_TOKEN="<JWT_TOKEN_HERE>"
```

### SLA Pass/Fail Criteria
| Metric | Threshold | Remediation if Failed |
| :--- | :--- | :--- |
| **p95 Latency** | $< 300\text{ ms}$ | Check gateway CPU throttling or database connection pool starvation. |
| **p99 Latency** | $< 800\text{ ms}$ | Review GC pause times in Spring Boot services via Grafana. |
| **Error Rate** | $< 1.0\%$ | Inspect gateway Morgan access logs for 502/503 upstream errors. |
| **HPA Scale-Out** | Scales up to 6–10 pods | Verify Metrics Server is running and CPU utilization metric is healthy. |

### Real-Time Monitoring during Test
```bash
# Watch HPA scaling in real-time
kubectl get hpa gateway-hpa -n twindigital -w

# Watch pod replica count expand
kubectl get pods -n twindigital -l app.kubernetes.io/name=gateway
```

---

## 4. Procedure 2: Chaos Engineering Scenarios

### Scenario 1: Pod CrashLoop & Alerting
- **Action**: Injects a failing entrypoint into `crashloop-canary` in `twindigital-chaos`.
- **Expected Outcome**: Restarts count $> 3$ within 5 minutes.
- **Alert Triggered**: `PodFrequentRestarts` alert in Alertmanager.
- **Verification Command**:
  ```bash
  kubectl get pods -n twindigital-chaos -l app=crashloop-canary -o wide
  curl -s http://localhost:9093/api/v2/alerts | jq '.[].labels.alertname'
  ```

### Scenario 2: Node Drain & Pod Rescheduling
- **Action**: Drains an active worker node (`kubectl drain <node> --ignore-daemonsets --delete-emptydir-data`).
- **Expected Outcome**: Kubernetes terminates pods on the target node, honors PodDisruptionBudgets (PDB), and reschedules them on the alternate worker node within 20 seconds.
- **Verification Command**:
  ```bash
  kubectl get pods -n twindigital -o wide
  # Confirm no pods are in Terminating or CrashLoopBackOff phase
  ```

### Scenario 3: Memory Stress Pod & cgroup OOM Protection
- **Action**: Deploys an isolated memory-hog container exceeding its 128Mi cgroup memory limit.
- **Expected Outcome**: Linux kernel cgroup OOM-killer terminates only the rogue pod (`OOMKilled`, exit code 137). Neighboring pods and host node memory remain completely unaffected.
- **Verification Command**:
  ```bash
  kubectl get pod memory-stress-hog -n twindigital-chaos \
    -o jsonpath='{.status.containerStatuses[0].state.terminated.reason}'
  # Returns: OOMKilled
  ```

### Scenario 4: Cascading Pod Termination (Kill Each Service)
- **Action**: Sequentially kills pods for `gateway`, `auth-service`, `cluster-sync-service`, `topology-service`, `simulation-service`, and `cost-risk-service`.
- **Expected Outcome**: Deployment controllers replace pods immediately. Pre-configured liveness/readiness probes prevent premature traffic routing until new pods are fully initialized. Zero client-facing downtime.

### Scenario 5: Redis Cache Outage
- **Action**: Scales Redis deployment to 0 replicas.
- **Expected Outcome**: Gateway falls back automatically to an in-memory rate limiter store; auth-service continues token signature validation locally without crashing or returning HTTP 500.

### Scenario 6: Network Partition (K8s API Egress Block)
- **Action**: Injects an egress-blocking NetworkPolicy cutting `cluster-sync-service` from the Kubernetes API.
- **Expected Outcome**:
  1. Informer watch connection drops.
  2. `GET /api/sync/status` transitions to `status: STALE`, `lagSeconds: > 0`.
  3. When NetworkPolicy is removed, `cluster-sync-service` reconnects via exponential backoff and returns to `HEALTHY`.
- **Verification Command**:
  ```bash
  curl -s http://localhost:8080/api/sync/status | jq .
  ```

---

## 5. Procedure 3: Simulation Accuracy Validation

### Purpose
Validates that the digital twin prediction engine accurately predicts the real-world outcome of proposed cluster changes (e.g. CPU/Memory exhaustion, admission webhook rejections, PDB violations, and blast radius impact).

### Execution Command
```bash
python docs/testing/simulation/simulation-accuracy-harness.py
```

### Output Artifacts
1. **Console Summary**: Displays per-scenario prediction vs. actual cluster behavior.
2. **CSV Report** ([`simulation_accuracy_results.csv`](./simulation/simulation_accuracy_results.csv)):
   - Columns: `Scenario_ID`, `Scenario_Name`, `Change_Kind`, `Target`, `Predicted_Risk`, `Actual_Risk`, `Predicted_Checks`, `Actual_Checks`, `Cluster_Behavior`, `Outcome_Match`, `Accuracy_Score`.
3. **Threshold**: Platform acceptance requires **$\ge 90.0\%$ accuracy** across all 10 fixture scenarios.

---

## 6. Incident Escalation & Post-Mortem Template

If any chaos or load test fails to meet acceptance thresholds:

1. **Collect Pod Logs & Events**:
   ```bash
   kubectl logs -n twindigital deploy/<service> --tail=200 > failure-logs.txt
   kubectl get events -n twindigital --sort-by='.lastTimestamp' > failure-events.txt
   ```
2. **Log Entry in Results Matrix**: Update [`RESULTS_TEMPLATE.md`](./RESULTS_TEMPLATE.md) with measured detection time, recovery time, and Grafana screenshot link.
3. **Trigger Rollback**: If failure occurs during deployment rollout, the CI/CD pipeline triggers an automatic `kubectl rollout undo`.
