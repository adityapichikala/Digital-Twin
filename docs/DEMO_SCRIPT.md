# TwinDigital 3-Minute Executive Demonstration Script

This script provides an exact, second-by-second walkthrough for demonstrating the TwinDigital platform to engineering leadership, platform engineers, and SREs.

* **Target Duration**: Exactly 3 minutes (180 seconds)
* **Demo Goal**: Prove real-time cluster synchronization, predictive "what-if" simulation accuracy, automated cost right-sizing, and zero-downtime resilience.

---

## Script Overview & Timeline

| Time Segment | Module Demonstrated | Screen / Route | Core Message |
| :---: | :--- | :--- | :--- |
| **0:00 – 0:45** | **Real-Time Topology & Blast Radius** | `http://localhost:5173/topology` | Sub-second Kubernetes sync & visual dependency graph. |
| **0:45 – 1:30** | **What-If Simulation Engine** | `http://localhost:5173/simulate` | Predict cluster failure & cost delta *before* committing change sets. |
| **1:30 – 2:15** | **Cost & PromQL Right-Sizing** | `http://localhost:5173/cost-risk` | 7-day p95 PromQL analysis & AWS Cost Explorer savings. |
| **2:15 – 3:00** | **Security Hardening & Auto-Rollback** | Terminal (`kubectl`) | Read-only rootfs, network policy defense & auto-rollback. |

---

## 0:00 – 0:45 | Scene 1: Real-Time Cluster Twin & Blast Radius

### Visual:
* Open browser to **`http://localhost:5173/topology`**.
* The screen displays the interactive **ReactFlow Canvas** with Dagre layout: Nodes $\rightarrow$ Pods $\rightarrow$ Deployments $\rightarrow$ Services.

### Action:
1. Click on the **`auth-service`** node. The right drawer slides open showing configured CPU requests (`150m`), memory limits (`512Mi`), and restart counts (`0`).
2. Point out the highlighted **Blast Radius** showing all upstream services (`gateway`, `simulation-service`, `topology-service`) highlighted in amber computed via an in-memory BFS traversal.
3. Switch to terminal and trigger a live pod deletion:
   ```bash
   kubectl delete pod -l app.kubernetes.io/name=billing-service -n twindigital
   ```
4. Immediately look back at the UI: watch the topology canvas reflect the pod termination and replacement on the **5-second REST poll** with zero manual refresh.

### Presenter Narration (What to Say):
> *"Welcome to TwinDigital. Traditional Kubernetes management is reactive—you deploy a change, pray, and watch Datadog for alerts.*
>
> *TwinDigital solves this by creating a true digital twin of your Amazon EKS cluster in `ap-south-1`. Notice how this directed graph is rendered live from our Kubernetes Informers—not fixed database dumps. Every pod, deployment, and service is mirrored into our graph model in sub-second time.*
>
> *When I click on `auth-service`, our topology-service executes an in-memory Breadth-First Search (BFS) to compute its blast radius. We see every downstream and upstream dependent highlighted in amber. And watch this: when I delete a live pod via `kubectl`, our List+Watch informer streams the delta into PostgreSQL and our React 19 frontend updates on its 5-second REST poll with zero delay."*

---

## 0:45 – 1:30 | Scene 2: What-If Simulation Engine in Action

### Visual:
* Click on **"Simulate"** in the top navigation bar (`/simulate`).
* The **Simulation Workbench** is displayed with change set controls on the left and audit history on the right.

### Action:
1. Select **Kind**: `SCALE`.
2. Target: **`heavy-worker`**.
3. Replicas: Set to **`6`**.
4. CPU Request: Set to **`1000m`** (1 core per pod = 6000m total).
5. Click **"Run Simulation"**.
6. The engine returns in ~80ms:
   - **Risk Level**: <span style="color:red; font-weight:bold;">CRITICAL (Score: 50/100)</span>
   - **Failed Check**: `SchedulingFit`
   - **Projected Cost Delta**: `+$142.30/mo` (calculated via `cost-risk-service`)
   - **Explanation**: *"Cluster capacity exhausted. Total cluster allocatable is 3860m; requested change requires 6000m CPU. Pods will enter permanently Pending status."*

### Presenter Narration (What to Say):
> *"Now let's see why TwinDigital changes how teams operate. Imagine a developer wants to scale our worker to 6 replicas, each requesting 1 full CPU core.*
>
> *Normally, applying this would silently exhaust our nodes, causing other workloads to be evicted and triggering a P1 incident.*
>
> *Instead, they submit the change to TwinDigital at `/simulate`. In 80 milliseconds, our deterministic simulation engine evaluates 5 discrete checks: SchedulingFit, LimitSanity, NodeRemovalImpact, HpaImpact, and BlastRadius, while querying `cost-risk-service` for the estimated monthly financial cost delta.*
>
> *It immediately flags this as CRITICAL. It bin-packs the pods against our real cluster capacity, detects that we only have 3860 millicores available, warns us that 3 pods will fail to schedule, and shows the cost impact. We prevented a production outage before touching a single YAML file."*

---

## 1:30 – 2:15 | Scene 3: PromQL 7-Day p95 Right-Sizing & AWS Cost Analysis

### Visual:
* Click on **"Cost & Risk"** in the top navigation (`/cost-risk`).
* The **Cluster Cost & Right-Sizing Dashboard** appears.

### Action:
1. Highlight the top KPI cards:
   - **Monthly Spend**: `$60.74/mo`
   - **Potential Savings**: <span style="color:green; font-weight:bold;">`$14.50/mo (24% of cluster spend)`</span>
   - **Over-Provisioned Pods**: `2`
2. Scroll to the **Right-Sizing Recommendations Table**:
   - Point to `billing-service-pod-1`:
     - Current Request: `500m CPU / 1024Mi RAM`
     - PromQL 7-day p95 Usage: `45m CPU (9%) / 180Mi RAM (17%)`
     - Recommended Right-Sizing: `57m CPU / 234Mi RAM`
3. Point out the Micrometer gauges:
   ```bash
   curl -s http://localhost:8085/actuator/prometheus | grep twin_
   # Outputs: twin_cost_estimated_usd 60.74
   #          twin_overprovisioned_pods 2.0
   ```

### Presenter Narration (What to Say):
> *"Next is cloud cost optimization. TwinDigital doesn't rely on guesswork or static rules. Our combined cost-risk-service queries the Prometheus PromQL HTTP API for true 7-day p95 CPU and memory working set metrics.*
>
> *Looking at our table, we see `billing-service` requesting 500m CPU and 1GB of memory. But its real 95th percentile usage over the past 7 days is only 45 millicores and 180 megabytes—over 85% waste!*
>
> *TwinDigital calculates right-sized values with a 25% safety buffer and integrates with the AWS Cost Explorer API via IRSA in `ap-south-1`, with an in-memory Caffeine cache so we don't get billed for duplicate queries. Right-sizing these two workloads immediately saves 24% of our cluster bill."*

---

## 2:15 – 3:00 | Scene 4: Security Hardening, Zero-Downtime Resilience & Rollback

### Visual:
* Switch to the terminal window to show live security enforcement and automatic rollback.

### Action 1 (Security Defense):
```bash
# Attempt to write to the container filesystem in gateway:
kubectl exec -n twindigital deploy/gateway -c gateway -- touch /root/malicious.sh
```
*Output*: `touch: /root/malicious.sh: Read-only file system`

### Action 2 (Automatic Rollback):
```bash
# Push an invalid Amazon ECR container image to trigger automatic rollback:
kubectl set image deployment/gateway gateway=112233445566.dkr.ecr.ap-south-1.amazonaws.com/twindigital/gateway:invalid-tag-v99 -n twindigital
kubectl rollout status deployment/gateway -n twindigital --timeout=15s
# Show pipeline or script triggering instant recovery:
kubectl rollout undo deployment/gateway -n twindigital
kubectl get pods -n twindigital -l app.kubernetes.io/name=gateway
```
*Output*: Pods immediately return to `Running` (100% healthy, zero dropped requests).

### Presenter Narration (What to Say):
> *"Finally, production security and operational resilience.*
>
> *All our manifests enforce Kubernetes Restricted Pod Security Standards: non-root user 10001, capabilities dropped, seccomp RuntimeDefault, and read-only root filesystems. When an attacker attempts to write an unauthorized binary, the kernel immediately blocks it.*
>
> *Our NetworkPolicies enforce default-deny isolation, and our 8-stage declarative Jenkins CI/CD pipeline runs Trivy image scanning, SonarQube quality gates, non-blocking OWASP ZAP DAST audits, and pushes to Amazon ECR.*
>
> *If a bad deployment is pushed, our pipeline catches the failure and triggers an automatic `rollout undo`, keeping the platform 100% available.*
>
> *TwinDigital delivers complete visibility, predictive safety, and cost efficiency for modern Kubernetes engineering teams. Thank you!"*

---

## Demo Checklist & Preparation

- [ ] Docker Compose / EKS services running (`postgres`, `redis`, `gateway`, all 5 Spring Boot 3.3 Java services).
- [ ] Frontend running on `http://localhost:5173`.
- [ ] Terminal open with `kubectl` context set to `twindigital` namespace in `ap-south-1`.
- [ ] Pre-flight sanity check: `curl http://localhost:8080/health` returns `UP`.
