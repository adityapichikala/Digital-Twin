# TwinDigital Kubernetes Manifests & Infrastructure Setup

This directory contains production-ready Kubernetes manifests structured with **Kustomize**, incorporating multi-tier security hardening, network policies, IAM Roles for Service Accounts (IRSA), External Secrets Operator (ESO), autoscaling (HPA), and Nginx Ingress routing.

---

## Directory Layout

```
k8s/
├── base/
│   ├── namespace.yaml                    # twindigital namespace with Pod Security restricted profile
│   ├── kustomization.yaml                # Base assembly
│   ├── apps/
│   │   ├── gateway/                      # Express API Gateway (Port 8080)
│   │   │   ├── deployment.yaml           # Hardened deployment (non-root, readOnlyRootFS, drop ALL)
│   │   │   ├── service.yaml              # ClusterIP service
│   │   │   ├── hpa.yaml                  # HPA (min: 2, max: 10, target CPU: 75%, RAM: 80%)
│   │   │   └── kustomization.yaml
│   │   ├── auth-service/                 # Spring Boot Auth (Port 8081)
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── kustomization.yaml
│   │   ├── cluster-sync-service/         # Spring Boot K8s Sync (Port 8082)
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── kustomization.yaml
│   │   ├── topology-service/             # Spring Boot Directed Graph Engine (Port 8083)
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── kustomization.yaml
│   │   ├── simulation-service/           # Spring Boot Simulation Engine (Port 8084)
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   ├── hpa.yaml                  # HPA (min: 2, max: 8, target CPU: 70%, RAM: 75%)
│   │   │   └── kustomization.yaml
│   │   ├── cost-risk-service/            # Spring Boot Cost & Right-Sizing (Port 8085)
│   │   │   ├── deployment.yaml           # IRSA ServiceAccount with Cost Explorer IAM
│   │   │   ├── service.yaml
│   │   │   └── kustomization.yaml
│   │   └── frontend/                     # React / Vite Nginx SPA (Port 80)
│   │       ├── deployment.yaml
│   │       ├── service.yaml
│   │       └── kustomization.yaml
│   ├── ingress/
│   │   ├── ingress.yaml                  # Nginx Ingress with TLS & security headers
│   │   └── kustomization.yaml
│   ├── rbac/
│   │   ├── service-accounts.yaml         # Dedicated SAs with automount token disabled where possible
│   │   ├── roles.yaml                    # Namespace-scoped least-privilege roles
│   │   ├── role-bindings.yaml            # RoleBindings
│   │   ├── cluster-roles.yaml            # Read-only ClusterRole for cluster-sync-service
│   │   └── kustomization.yaml
│   ├── network-policies/
│   │   ├── default-deny.yaml             # Default-deny-all ingress & egress
│   │   ├── allow-dns.yaml                # CoreDNS UDP/TCP 53 egress
│   │   ├── gateway-netpol.yaml           # Gateway ingress from Nginx, egress to services & redis
│   │   ├── services-netpol.yaml          # Inter-service routing & Postgres/Redis egress
│   │   ├── cost-risk-netpol.yaml         # Cost-risk egress to Prometheus & AWS API (443)
│   │   ├── cluster-sync-netpol.yaml      # Cluster-sync egress to K8s API (443/6443) & Postgres
│   │   ├── ingress-frontend-netpol.yaml  # Frontend ingress from Nginx
│   │   ├── prometheus-metrics-netpol.yaml# Prometheus scraping ingress (8080-8085)
│   │   └── kustomization.yaml
│   └── external-secrets/
│       ├── secret-store.yaml             # ESO SecretStore with AWS Secrets Manager IRSA
│       ├── external-secrets.yaml         # Postgres, JWT, and Redis secret syncing
│       └── kustomization.yaml
├── terraform/
│   ├── irsa.tf                           # AWS IAM Roles & strict policies for IRSA
│   └── secrets.tf                        # AWS Secrets Manager KMS resources
├── monitoring/                           # Prometheus & Grafana manifests
├── kustomization.yaml                    # Root Kustomize file
└── SECURITY.md                           # Comprehensive security controls & verification guide
```

---

## Deployment Quickstart

### 1. Provision AWS IAM Roles & Secrets via Terraform
```bash
cd k8s/terraform
terraform init
terraform apply -var="aws_account_id=<YOUR_ACCOUNT_ID>" -var="aws_region=ap-south-1"
```

### 2. Preview Kustomize Manifests
```bash
kubectl kustomize k8s/
```

### 3. Deploy to Kubernetes
```bash
kubectl apply -k k8s/
```

### 4. Verify Deployments & Pods
```bash
kubectl get pods,svc,hpa,ingress -n twindigital
```

### 5. Verify Security Controls
See [`SECURITY.md`](./SECURITY.md) for full explanations and runnable `kubectl` verification commands for read-only rootfs, non-root user, capability drops, default-deny network policies, IRSA, and External Secrets.
