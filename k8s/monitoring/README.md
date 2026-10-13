# TwinDigital Observability Stack (`/k8s/monitoring`)

Comprehensive Kubernetes observability configuration for TwinDigital on Amazon EKS using **kube-prometheus-stack** (Prometheus, Grafana, Alertmanager), customized for a 2-node `t3.medium` cluster.

---

## 📁 Directory Structure

```text
k8s/monitoring/
├── README.md                                # This deployment and operational guide
├── helm/
│   └── kube-prometheus-stack-values.yaml    # 15s scrape, 15d retention, t3.medium sizing, Grafana & Alertmanager
├── servicemonitors/
│   ├── gateway-servicemonitor.yaml          # Gateway (/metrics, port 3000)
│   └── spring-boot-servicemonitors.yaml     # 5 Spring Boot microservices (/actuator/prometheus)
├── alerts/
│   └── twindigital-alerts.yaml              # Pod restarts > 3 in 5m, Node memory pressure > 2m, API error rate > 5%
├── alertmanager/
│   ├── alertmanager-secret.yaml             # AWS SES SMTP credentials secret template
│   └── alertmanager-config.yaml             # AlertmanagerConfig CRD with SES email routing
└── dashboards/
    ├── cluster-overview-dashboard.yaml      # Cluster nodes CPU/RAM, pod status, restarts ConfigMap
    ├── service-metrics-dashboard.yaml       # Gateway & microservices latency (P50/P95/P99), RPS, 5xx error rate
    └── cost-trend-dashboard.yaml            # FinOps metrics: twin_cost_estimated_usd & twin_overprovisioned_pods
```

---

## 🚀 Quickstart Deployment Guide

### Prerequisites
- Configured `kubectl` pointing to your EKS cluster:
  ```bash
  aws eks update-kubeconfig --region ap-south-1 --name twindigital-dev
  ```
- Helm 3.x installed (`helm version`)

---

### Step 1: Create the Monitoring Namespace

```bash
kubectl create namespace monitoring
kubectl create namespace twindigital --dry-run=client -o yaml | kubectl apply -f -
```

---

### Step 2: Create AWS SES SMTP Credentials Secret

Alertmanager credentials must **never** be checked into version control or specified in plain text.

#### Option A: Via `kubectl create secret` (Recommended for CI/CD)
```bash
kubectl create secret generic alertmanager-ses-smtp \
  --namespace monitoring \
  --from-literal=smtp-username='<YOUR_AWS_SES_SMTP_USERNAME>' \
  --from-literal=smtp-password='<YOUR_AWS_SES_SMTP_PASSWORD>' \
  --from-literal=smtp-smarthost='email-smtp.ap-south-1.amazonaws.com:587' \
  --from-literal=smtp-from='alerts@twindigital.io'
```

#### Option B: Populate and Apply `alertmanager/alertmanager-secret.yaml`
```bash
kubectl apply -f k8s/monitoring/alertmanager/alertmanager-secret.yaml
```

---

### Step 3: Add Helm Repository and Install kube-prometheus-stack

```bash
# 1. Add Prometheus Community Helm Repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 2. Install / Upgrade kube-prometheus-stack
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values k8s/monitoring/helm/kube-prometheus-stack-values.yaml \
  --wait --timeout 10m
```

---

### Step 4: Apply Alertmanager Configuration

```bash
kubectl apply -f k8s/monitoring/alertmanager/alertmanager-config.yaml
```

---

### Step 5: Deploy ServiceMonitors & Alert Rules

```bash
# Apply Gateway and Microservices ServiceMonitors
kubectl apply -f k8s/monitoring/servicemonitors/gateway-servicemonitor.yaml
kubectl apply -f k8s/monitoring/servicemonitors/spring-boot-servicemonitors.yaml

# Apply PrometheusRule Alerts
kubectl apply -f k8s/monitoring/alerts/twindigital-alerts.yaml
```

---

### Step 6: Deploy Grafana Dashboards

The Grafana sidecar automatically detects any ConfigMap with label `grafana_dashboard: "1"` in any namespace and mounts it into Grafana:

```bash
kubectl apply -f k8s/monitoring/dashboards/cluster-overview-dashboard.yaml
kubectl apply -f k8s/monitoring/dashboards/service-metrics-dashboard.yaml
kubectl apply -f k8s/monitoring/dashboards/cost-trend-dashboard.yaml
```

---

## 🔍 Verification & Port-Forwarding

### 1. Verify Monitoring Pods Status
```bash
kubectl get pods -n monitoring
```
Expected output:
- `kube-prometheus-stack-grafana-...` (Running)
- `kube-prometheus-stack-operator-...` (Running)
- `prometheus-kube-prometheus-stack-prometheus-0` (Running)
- `alertmanager-kube-prometheus-stack-alertmanager-0` (Running)

### 2. Verify ServiceMonitors and PrometheusRules
```bash
kubectl get servicemonitors -n monitoring
kubectl get prometheusrules -n monitoring
```

### 3. Port-Forward Grafana (Web UI)
```bash
kubectl port-forward svc/kube-prometheus-stack-grafana 3001:80 -n monitoring
```
- **URL**: [http://localhost:3001](http://localhost:3001)
- **Username**: `admin`
- **Password**: Defined in `kube-prometheus-stack-values.yaml` (`admin-changeme-prompt`) or retrieve dynamic password:
  ```bash
  kubectl get secret -n monitoring kube-prometheus-stack-grafana -o jsonpath="{.data.admin-password}" | base64 --decode ; echo
  ```

### 4. Port-Forward Prometheus (Web UI & Targets)
```bash
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
```
- **URL**: [http://localhost:9090](http://localhost:9090)
- Navigate to **Status > Targets** to verify that all 5 Spring Boot services and the Gateway are listed in state **UP**.

### 5. Port-Forward Alertmanager (Web UI)
```bash
kubectl port-forward svc/kube-prometheus-stack-alertmanager 9093:9093 -n monitoring
```
- **URL**: [http://localhost:9093](http://localhost:9093)

---

## 📊 Summary of Dashboards & Metrics

| Dashboard | File | Key Metrics & PromQL Queries |
|---|---|---|
| **Cluster Overview** | `cluster-overview-dashboard.yaml` | Node CPU/RAM % utilization, Pod phase distribution, Pod restarts in past 1h, Disk usage |
| **Service Latency & Errors** | `service-metrics-dashboard.yaml` | Gateway RPS, P50/P95/P99 latency, 5xx error %, Spring Boot throughput, JVM Heap & GC pauses |
| **FinOps Cost Trends** | `cost-trend-dashboard.yaml` | `twin_cost_estimated_usd` (Live vs Simulated scenario delta), `twin_overprovisioned_pods`, CPU/Memory allocatable efficiency |

---

## 🚨 Alert Rules Overview

| Alert Name | Severity | Condition | Threshold / Duration |
|---|---|---|---|
| `PodFrequentRestarts` | Warning | `increase(kube_pod_container_status_restarts_total[5m]) > 3` | > 3 restarts within 5m |
| `NodeMemoryPressureCondition` | Critical | `kube_node_status_condition{condition="MemoryPressure",status="true"} == 1` | Active condition for > 2m |
| `NodeMemoryUsageCritical` | Critical | `(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100 > 85` | Memory usage > 85% for > 2m |
| `GatewayHighErrorRate` | Critical | `(sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))) * 100 > 5` | 5xx error rate > 5% for > 2m |
| `MicroserviceHighErrorRate` | Critical | `sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / ... > 5` | 5xx error rate > 5% for > 2m |
| `ServiceInstanceDown` | Critical | `up{job=~"twindigital.*"} == 0` | Scrape target unreachable for > 1m |
| `SimulationLatencyP99High` | Warning | `histogram_quantile(0.99, ...) > 2` | Simulation P99 response time > 2s |
