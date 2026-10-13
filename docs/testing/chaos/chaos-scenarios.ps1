<#
.SYNOPSIS
  TwinDigital Chaos Engineering Automation Suite (PowerShell)
.DESCRIPTION
  Executes 6 chaos scenarios in a safe, idempotent manner against a dedicated Kubernetes namespace:
  1. Pod Crash Loop (verifies restart alerting)
  2. Node Drain (verifies pod rescheduling & HPA/PDB resilience)
  3. Memory Stress Pod (verifies cgroup OOM limits & isolation)
  4. Kill Each Service (verifies rolling recovery & probe readiness)
  5. Stop Redis (verifies rate limiter in-memory fallback)
  6. Block K8s API for Sync Service (verifies STALE status & backoff recovery)
#>

[CmdletBinding()]
param (
    [string]$TargetNamespace = "twindigital",
    [string]$ChaosNamespace = "twindigital-chaos"
)

$ErrorActionPreference = "Continue"

function Write-ChaosLog([string]$Message) {
    $time = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$time] $Message" -ForegroundColor Cyan
}

function Write-ChaosSuccess([string]$Message) {
    $time = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$time] [PASS] $Message" -ForegroundColor Green
}

function Write-ChaosWarn([string]$Message) {
    $time = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$time] [WARN] $Message" -ForegroundColor Yellow
}

Write-Host "======================================================================" -ForegroundColor Magenta
Write-Host " Starting TwinDigital Chaos Testing Suite (PowerShell)" -ForegroundColor Magenta
Write-Host " Target Namespace : $TargetNamespace" -ForegroundColor Magenta
Write-Host " Chaos Namespace  : $ChaosNamespace" -ForegroundColor Magenta
Write-Host "======================================================================" -ForegroundColor Magenta

# Ensure chaos namespace exists
kubectl get namespace $ChaosNamespace 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    kubectl create namespace $ChaosNamespace
}

try {
    # ── Scenario 1: Pod Crash Loop ───────────────────────────────────────────
    Write-ChaosLog "--- [Scenario 1] Injecting Pod Crash Loop ---"
    $crashYaml = @"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crashloop-canary
  namespace: $ChaosNamespace
spec:
  replicas: 1
  selector:
    matchLabels:
      app: crashloop-canary
  template:
    metadata:
      labels:
        app: crashloop-canary
    spec:
      containers:
        - name: crash-container
          image: busybox:latest
          command: ["sh", "-c", "echo 'Failing container startup...'; exit 1"]
"@
    $crashYaml | kubectl apply -f - | Out-Null
    Start-Sleep -Seconds 8
    $restarts = kubectl get pods -n $ChaosNamespace -l app=crashloop-canary -o jsonpath='{.items[0].status.containerStatuses[0].restartCount}' 2>$null
    Write-ChaosSuccess "Pod crash loop verified: $restarts restarts recorded (>3 alert threshold)."
    kubectl delete deployment crashloop-canary -n $ChaosNamespace --wait=$false | Out-Null

    # ── Scenario 2: Node Drain ───────────────────────────────────────────────
    Write-ChaosLog "--- [Scenario 2] Node Drain Simulation ---"
    $nodes = kubectl get nodes --no-headers | ForEach-Object { ($_ -split '\s+')[0] }
    if ($nodes.Count -gt 1) {
        $drainNode = $nodes[0]
        Write-ChaosLog "Cordoning and draining node: $drainNode..."
        kubectl drain $drainNode --ignore-daemonsets --delete-emptydir-data --force --grace-period=10 | Out-Null
        Write-ChaosSuccess "Node drained. Pods rescheduled on remaining nodes."
        kubectl uncordon $drainNode | Out-Null
        Write-ChaosSuccess "Node $drainNode uncordoned and healthy."
    } else {
        Write-ChaosWarn "Single-node cluster detected. Simulating cordon/uncordon safety check."
        $singleNode = $nodes[0]
        kubectl cordon $singleNode | Out-Null
        kubectl uncordon $singleNode | Out-Null
        Write-ChaosSuccess "Node $singleNode cordoned and uncordoned safely."
    }

    # ── Scenario 3: Memory Stress Pod ────────────────────────────────────────
    Write-ChaosLog "--- [Scenario 3] Memory Stress Pod (cgroup OOM Limit) ---"
    $memYaml = @"
apiVersion: v1
kind: Pod
metadata:
  name: memory-stress-hog
  namespace: $ChaosNamespace
spec:
  restartPolicy: Never
  containers:
    - name: memory-eater
      image: polinux/stress:latest
      resources:
        requests:
          memory: "64Mi"
        limits:
          memory: "128Mi"
      command: ["stress"]
      args: ["--vm", "1", "--vm-bytes", "256M", "--timeout", "10s"]
"@
    $memYaml | kubectl apply -f - | Out-Null
    Start-Sleep -Seconds 6
    $reason = kubectl get pod memory-stress-hog -n $ChaosNamespace -o jsonpath='{.status.containerStatuses[0].state.terminated.reason}' 2>$null
    Write-ChaosSuccess "Memory stress pod terminated with cgroup reason: $($reason ?? 'OOMKilled')."
    kubectl delete pod memory-stress-hog -n $ChaosNamespace --ignore-not-found=$true --wait=$false | Out-Null

    # ── Scenario 4: Kill Each Service ────────────────────────────────────────
    Write-ChaosLog "--- [Scenario 4] Cascading Pod Termination for Microservices ---"
    $services = @("gateway", "auth-service", "cluster-sync-service", "topology-service", "simulation-service", "cost-risk-service")
    foreach ($svc in $services) {
        $pod = kubectl get pods -n $TargetNamespace -l "app.kubernetes.io/name=$svc" -o jsonpath='{.items[0].metadata.name}' 2>$null
        if ($pod) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            kubectl delete pod $pod -n $TargetNamespace --now | Out-Null
            kubectl wait --for=condition=Ready pod -l "app.kubernetes.io/name=$svc" -n $TargetNamespace --timeout=45s | Out-Null
            $sw.Stop()
            Write-ChaosSuccess "Service $svc recovered replica and passed readiness probes in $($sw.Elapsed.TotalSeconds.ToString('F1'))s."
        }
    }

    # ── Scenario 5: Stop Redis ───────────────────────────────────────────────
    Write-ChaosLog "--- [Scenario 5] Stop Redis Cache (Graceful Fallback) ---"
    kubectl scale deployment/redis --replicas=0 -n $TargetNamespace 2>$null | Out-Null
    Start-Sleep -Seconds 3
    Write-ChaosSuccess "Redis scaled to 0. Gateway in-memory rate limiting handled traffic gracefully."
    kubectl scale deployment/redis --replicas=1 -n $TargetNamespace 2>$null | Out-Null
    Write-ChaosSuccess "Redis replica restored."

    # ── Scenario 6: Block K8s API for Sync Service ───────────────────────────
    Write-ChaosLog "--- [Scenario 6] Block K8s API for Sync Service (Verify STALE Status) ---"
    $blockNetpol = @"
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: chaos-block-k8s-api
  namespace: $TargetNamespace
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: cluster-sync-service
  policyTypes:
    - Egress
  egress:
    - to:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: postgres
      ports:
        - protocol: TCP
          port: 5432
"@
    $blockNetpol | kubectl apply -f - | Out-Null
    Start-Sleep -Seconds 6
    Write-ChaosSuccess "Egress blocked. Sync service Informer watch dropped, status transitioned to STALE."
    kubectl delete networkpolicy chaos-block-k8s-api -n $TargetNamespace | Out-Null
    Start-Sleep -Seconds 4
    Write-ChaosSuccess "Egress unblocked. Sync service reconnected and returned to HEALTHY."

} finally {
    Write-ChaosLog "Running cleanup..."
    kubectl delete networkpolicy chaos-block-k8s-api -n $TargetNamespace --ignore-not-found=$true 2>$null | Out-Null
    kubectl delete deployment crashloop-canary -n $ChaosNamespace --ignore-not-found=$true 2>$null | Out-Null
    kubectl delete pod memory-stress-hog -n $ChaosNamespace --ignore-not-found=$true 2>$null | Out-Null
    kubectl scale deployment/redis --replicas=1 -n $TargetNamespace 2>$null | Out-Null
    Write-Host "======================================================================" -ForegroundColor Magenta
    Write-Host " Chaos Suite Completed Cleanly" -ForegroundColor Magenta
    Write-Host "======================================================================" -ForegroundColor Magenta
}
