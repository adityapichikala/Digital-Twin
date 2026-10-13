# TwinDigital Security Architecture & Hardening Controls Guide

This document details the production-grade security architecture implemented in the Kubernetes manifests (`/k8s`), explaining each control, its rationale, and executable `kubectl` commands to verify and demonstrate each defense.

---

## Security Control Matrix

| Control Category | Implementation | Threat Mitigated |
| :--- | :--- | :--- |
| **Filesystem Immutability** | `readOnlyRootFilesystem: true` + in-memory `emptyDir` for `/tmp` | Persistent malware installation, tampering with binaries/scripts |
| **Identity & Privilege** | `runAsNonRoot: true`, `runAsUser: 10001`, `runAsGroup: 10001` | Container breakout to host root, unauthorized file ownership change |
| **Kernel Hardening** | `capabilities: drop: ["ALL"]` | Linux kernel privilege escalation (`CAP_NET_RAW`, `CAP_SYS_ADMIN`) |
| **Syscall Filtering** | `seccompProfile: type: RuntimeDefault` | Zero-day kernel exploits via dangerous system calls |
| **Network Segmentation** | `default-deny-all` NetworkPolicy + explicit allow rules | Lateral movement, port scanning, data exfiltration |
| **Least-Privilege RBAC** | `automountServiceAccountToken: false` + read-only ClusterRoles | Cluster credential theft, unauthorized K8s API operations |
| **Cloud IAM Isolation** | EKS IRSA + explicit `Deny` on non-Cost-Explorer AWS APIs | AWS account compromise, privilege escalation via IAM |
| **Secrets Management** | External Secrets Operator (ESO) + AWS Secrets Manager KMS | Plaintext secrets in Git or ConfigMaps, credential leakage |

---

## 1. Read-Only Root Filesystem (`readOnlyRootFilesystem: true`)

### Architecture Rationale
Containers run with immutable root filesystems. Attackers cannot drop backdoor binaries, alter application JARs/scripts, or write persistent rootkits. Temporary application scratch space (e.g., Tomcat embedded workdir, JVM PID files, Nginx cache) is delegated to in-memory `emptyDir` volumes mounted strictly at `/tmp` and `/var/run`.

### Verification & Demonstration Commands

#### Demonstration: Unauthorized write is rejected
```bash
# Attempt to write a malicious script into the container root filesystem
kubectl exec -n twindigital deploy/gateway -c gateway -- touch /root/malicious.sh
```
**Expected Output:**
```text
touch: /root/malicious.sh: Read-only file system
command terminated with exit code 1
```

#### Verification: Authorized scratch space `/tmp` remains writable
```bash
# Verify legitimate temporary scratch space works as expected
kubectl exec -n twindigital deploy/gateway -c gateway -- touch /tmp/test-ok.tmp
kubectl exec -n twindigital deploy/gateway -c gateway -- rm /tmp/test-ok.tmp
```
**Expected Output:**
```text
Exit code 0 (write succeeds in mounted emptyDir)
```

---

## 2. Non-Root User Enforcement (`runAsNonRoot: true`)

### Architecture Rationale
Containers run as unprivileged user `10001:10001` (UID 10001, GID 10001). If a process is compromised, the attacker cannot leverage root host mapping (`UID 0`) to access sensitive host device nodes or files.

### Verification & Demonstration Commands

```bash
# Inspect process UID and GID inside the running container
kubectl exec -n twindigital deploy/auth-service -c auth-service -- id
```
**Expected Output:**
```text
uid=10001(appuser) gid=10001(appgroup) groups=10001(appgroup)
```

```bash
# Verify Pod Security Admission blocks any pod attempting to run as root:
kubectl run rogue-root-test --namespace=twindigital --image=busybox --restart=Never \
  --overrides='{"spec":{"securityContext":{"runAsUser":0}}}' -- sleep 10
```
**Expected Output:**
```text
Error from server (Forbidden): pods "rogue-root-test" is forbidden: violates PodSecurity "restricted:latest": runAsUser=0 (container "rogue-root-test" must not set runAsUser=0)
```

---

## 3. Drop All Linux Capabilities (`capabilities: drop: ["ALL"]`)

### Architecture Rationale
By default, Docker/containerd grants default Linux capabilities (`CAP_CHOWN`, `CAP_FOWNER`, `CAP_NET_RAW`). TwinDigital manifests strip **all** capabilities (`drop: ["ALL"]`), completely disabling raw packet sockets (preventing ARP/IP spoofing) and file ownership overrides.

### Verification & Demonstration Commands

```bash
# Check effective and permitted capability bitmasks in process status
kubectl exec -n twindigital deploy/simulation-service -c simulation-service -- cat /proc/1/status | grep -E "^Cap"
```
**Expected Output:**
```text
CapInh: 0000000000000000
CapPrm: 0000000000000000
CapEff: 0000000000000000
CapBnd: 0000000000000000
CapAmb: 0000000000000000
```

---

## 4. Seccomp Profile `RuntimeDefault`

### Architecture Rationale
Restricts available syscalls to the container runtime's hardened allowlist, filtering out syscalls associated with container breakouts (such as `keyctl`, `process_vm_writev`, `ptrace`).

### Verification & Demonstration Commands

```bash
# Confirm RuntimeDefault is applied to the pod spec
kubectl get pod -n twindigital -l app.kubernetes.io/name=gateway \
  -o jsonpath='{.items[0].spec.securityContext.seccompProfile.type}'
```
**Expected Output:**
```text
RuntimeDefault
```

---

## 5. Default-Deny NetworkPolicies & Strict Whitelisting

### Architecture Rationale
A cluster-wide `default-deny-all` NetworkPolicy blocks all ingress and egress in `twindigital`. Microservices can **only** talk along explicit paths defined in individual policies:
1. `ingress-nginx` $\rightarrow$ `gateway:8080`, `frontend:80`
2. `gateway` $\rightarrow$ internal services (`auth:8081`, `sync:8082`, `topology:8083`, `simulation:8084`, `cost-risk:8085`) and `redis:6379`
3. `services` $\rightarrow$ `postgres:5432` and `redis:6379`
4. `simulation-service` $\rightarrow$ `topology:8083`, `cost-risk:8085`, `postgres:5432`
5. `cost-risk-service` $\rightarrow$ `prometheus:9090` and external AWS HTTPS (`443`)
6. `cluster-sync-service` $\rightarrow$ Kubernetes API Server (`443/6443`) and `postgres:5432`
7. `monitoring/prometheus` $\rightarrow$ services (`8080-8085` `/metrics` & `/actuator/prometheus`)

### Verification & Demonstration Commands

#### Demonstration A: Blocked Unauthorized Egress (Gateway attempting connection to external IP)
```bash
# API Gateway tries to reach an arbitrary IP on the internet
kubectl exec -n twindigital deploy/gateway -c gateway -- timeout 3 curl -s -I http://1.1.1.1:80
```
**Expected Output:**
```text
command terminated with exit code 124 (Connection timed out - dropped by default-deny egress)
```

#### Demonstration B: Blocked Inter-Service Lateral Movement
```bash
# auth-service attempts to directly call cluster-sync-service (unauthorized path)
kubectl exec -n twindigital deploy/auth-service -c auth-service -- timeout 3 curl -s http://cluster-sync-service:8082/health
```
**Expected Output:**
```text
command terminated with exit code 124 (Connection timed out - dropped by cluster-sync-service-netpol ingress)
```

#### Demonstration C: Allowed Authorized Path (Gateway to Auth Service)
```bash
# Legitimate traffic from Gateway to backend auth service
kubectl exec -n twindigital deploy/gateway -c gateway -- curl -s http://auth-service:8081/actuator/health
```
**Expected Output:**
```json
{"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
```

---

## 6. Namespace-Scoped RBAC & Token Disablement

### Architecture Rationale
- For microservices without Kubernetes API requirements (`gateway`, `auth-service`, `topology-service`, `frontend`), `automountServiceAccountToken: false` is configured. This prevents any compromised application from using a token to query the K8s API.
- For `cluster-sync-service`, a dedicated read-only `ClusterRole` allows only `get`, `list`, `watch` on Nodes, Pods, Services, Deployments, HPAs, and PDBs.
- For `simulation-service`, a scoped `Role` allows dry-run creation (`dryRun=All`) without cluster mutation privileges.

### Verification & Demonstration Commands

```bash
# 1. Verify service account token is NOT mounted inside gateway:
kubectl exec -n twindigital deploy/gateway -c gateway -- ls -d /var/run/secrets/kubernetes.io/serviceaccount
```
**Expected Output:**
```text
ls: /var/run/secrets/kubernetes.io/serviceaccount: No such file or directory
```

```bash
# 2. Verify cluster-sync-service SA can read cluster nodes:
kubectl auth can-i get nodes --as=system:serviceaccount:twindigital:cluster-sync-service-sa
```
**Expected Output:**
```text
yes
```

```bash
# 3. Verify cluster-sync-service SA CANNOT modify or delete pods:
kubectl auth can-i delete pods --as=system:serviceaccount:twindigital:cluster-sync-service-sa
```
**Expected Output:**
```text
no
```

---

## 7. IAM Roles for Service Accounts (IRSA) Isolation

### Architecture Rationale
`cost-risk-service` needs access to the AWS Cost Explorer API. Instead of storing long-lived AWS IAM access keys in Kubernetes secrets, it assumes an IAM Role via EKS Web Identity Token federation.
The attached IAM policy strictly allows `ce:*` and `pricing:*` while containing an **explicit Deny** statement for all other AWS services.

### Verification & Demonstration Commands

```bash
# 1. Verify EKS projected web identity token & role ARN are injected:
kubectl exec -n twindigital deploy/cost-risk-service -c cost-risk-service -- env | grep AWS_
```
**Expected Output:**
```text
AWS_ROLE_ARN=arn:aws:iam::112233445566:role/twindigital-cost-risk-irsa
AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
AWS_REGION=ap-south-1
```

```bash
# 2. Verify Cost Explorer queries succeed, but unauthorized AWS S3 calls fail:
# (Tested via AWS CLI or SDK in pod)
aws ce get-cost-and-usage ...  -> 200 OK
aws s3 ls                     -> AccessDenied (Explicit Deny in IAM policy)
```

---

## 8. External Secrets Operator (ESO) Synchronization

### Architecture Rationale
Sensitive database passwords, JWT secrets, and Redis keys are maintained in AWS Secrets Manager with KMS CMK encryption. The External Secrets Operator periodically reconciles these secrets into standard Kubernetes Secrets (`postgres-credentials`, `jwt-secrets`, `redis-credentials`) using the `external-secrets-sa` IRSA role.

### Verification & Demonstration Commands

```bash
# Verify ExternalSecrets status and reconciliation:
kubectl get externalsecrets -n twindigital
```
**Expected Output:**
```text
NAME                      STORE                      REFRESH INTERVAL   STATUS         READY
postgres-credentials-sync aws-secretsmanager-store   1h                 SecretSynced   True
jwt-secrets-sync          aws-secretsmanager-store   1h                 SecretSynced   True
redis-credentials-sync    aws-secretsmanager-store   1h                 SecretSynced   True
```

```bash
# Verify Kubernetes Secrets generated:
kubectl get secret -n twindigital postgres-credentials jwt-secrets redis-credentials
```
**Expected Output:**
```text
NAME                 TYPE     DATA   AGE
postgres-credentials Opaque   5      10m
jwt-secrets          Opaque   2      10m
redis-credentials    Opaque   2      10m
```

---

## 9. Horizontal Pod Autoscaler (HPA) Verification

### Verification Command

```bash
kubectl get hpa -n twindigital
```
**Expected Output:**
```text
NAME                     REFERENCE                       TARGETS           MINPODS   MAXPODS   REPLICAS
gateway-hpa              Deployment/gateway              cpu: 18%/75%      2         10        2
simulation-service-hpa   Deployment/simulation-service   cpu: 12%/70%      2         8         2
```
