# ADR-002: Cluster State Synchronization — Kubernetes Watch/Informers vs. Fixed-Interval Polling

* **Status**: `Accepted`
* **Date**: October 2026
* **Deciders**: Backend Engineering, Kubernetes Platform Team

---

## 1. Context and Problem Statement

The `cluster-sync-service` is the foundational data ingestion pipeline of TwinDigital. It must mirror live Amazon EKS cluster state (Nodes, Pods, Services, Deployments, HorizontalPodAutoscalers, and PodDisruptionBudgets) into PostgreSQL to power the digital twin graph engine and what-if simulation model.

We had to determine how to synchronize state from the Kubernetes API:
* **Option A**: Fixed-interval polling (e.g. querying the Kubernetes REST API every 15–30 seconds).
* **Option B**: Kubernetes Client `SharedIndexInformer` (Initial `List` + HTTP Streaming `Watch` with DeltaFIFO queue).

---

## 2. Decision Drivers

* **Event Latency & Real-Time Accuracy**: Digital twin simulations must evaluate cluster state based on the current cluster reality, not stale minutes-old snapshots.
* **API Server Load & Scalability**: Polling creates severe API server CPU pressure and etcd query thrashing as the number of cluster resources scales.
* **Transient Event Capture**: Short-lived pods (cronjobs, init containers, spot terminations) that start and finish between polling intervals must not be lost.
* **Failure Detection & Resiliency**: The system must immediately detect when communication with the Kubernetes control plane is interrupted, mark state as `STALE`, and recover automatically.

---

## 3. Considered Options

* **Option 1**: Fixed-Interval Polling (`@Scheduled(fixedDelay = 15000)` querying K8s API REST endpoints).
* **Option 2**: Official Kubernetes Java Client `SharedInformerFactory` (`List + Watch` pattern).

---

## 4. Decision Outcome

**Chosen Option: Option 2 — Kubernetes SharedIndexInformer (List + Watch)**

We selected the **`SharedIndexInformer`** mechanism implemented in [`cluster-sync-service`](../../services/cluster-sync-service).

### Technical Comparison

| Dimension | Fixed-Interval Polling | SharedIndexInformer (List + Watch) |
| :--- | :--- | :--- |
| **API Server Load** | **High & Inefficient**: Full resource dumps requested repeatedly, overloading etcd and API server CPU. | **Ultra-Low Overhead**: Initial `List` once per startup, followed by lightweight HTTP chunked streaming delta events. |
| **Update Latency** | **$15–30\text{ seconds}$ lag** depending on schedule interval. | **Sub-second ($< 100\text{ ms}$)**: Events dispatched immediately upon etcd commit. |
| **Transient Events** | **Missed entirely**: Pods terminating between polls vanish without record. | **100% Captured**: DeltaFIFO guarantees delivery of all `Add`, `Update`, and `Delete` events. |
| **Network Consumption**| Massive bandwidth bursts transferring full JSON arrays every tick. | Minimal bandwidth: Only transfers individual serialized resource diffs. |
| **Staleness Tracking** | Hard to detect if API server is partially degraded or silently failing. | Built-in: Watch drop triggers `STALE` health status and exponential backoff reconnection. |

---

## 5. Architectural Implementation Details

1. **DeltaFIFO Event Processing**:
   ```java
   podInformer.addEventHandler(new ResourceEventHandler<V1Pod>() {
       @Override public void onAdd(V1Pod pod) { syncPod(pod); }
       @Override public void onUpdate(V1Pod old, V1Pod cur) { syncPod(cur); }
       @Override public void onDelete(V1Pod pod, boolean unknown) { deletePod(pod); }
   });
   ```
2. **Staleness & Backoff Protection**:
   - If the HTTP watch stream disconnects, `cluster-sync-service` marks local cluster status as `STALE` and reports sync lag via `/api/sync/status`.
   - The informer reconnects using HTTP `resourceVersion` resumption, avoiding redundant full list queries.

---

## 6. Consequences

### Positive:
* Immediate responsiveness in the backend database: deleting a pod via `kubectl delete pod` persists the delta into PostgreSQL in less than 500 milliseconds, displayed in the ReactFlow UI on the next 5-second poll without manual page reload.
* Drastically reduced load on the EKS control plane.

### Negative / Trade-offs:
* Requires maintaining long-lived TCP watch connections and threading pools inside the Spring Boot container.
* Reconnection logic must handle etcd `too old resource version` 410 errors by triggering a clean relist.
