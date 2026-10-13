# ADR-004: Container Image Registry — Amazon ECR vs. Docker Hub

* **Status**: `Accepted`
* **Date**: October 2026
* **Deciders**: DevOps Team, Cloud Security Engineering

---

## 1. Context and Problem Statement

TwinDigital builds and deploys 7 container images across its frontend, API gateway, and 5 backend microservices. The target runtime environment is Amazon Elastic Kubernetes Service (EKS) in region `ap-south-1`.

We evaluated whether to store production artifacts in **Amazon Elastic Container Registry (ECR)** or **Docker Hub**.

---

## 2. Decision Drivers

* **Authentication & Credential Lifecycle**: Eliminate long-lived static tokens from Kubernetes secrets and Jenkins credentials.
* **Network Latency & AWS Data Transfer Fees**: Avoid traversing external internet gateways for container layer downloads.
* **Autoscaling Reliability**: Guarantee that cluster scale-out events (HPA scaling 2 $\to$ 10 replicas) never fail due to third-party image pull rate limits.
* **Vulnerability Scanning**: Automated scanning of pushed images for CVEs before deployment to staging/production.
* **Cost Efficiency**: Minimize recurring per-seat and bandwidth costs.

---

## 3. Considered Options

* **Option 1**: Amazon Elastic Container Registry (Amazon ECR).
* **Option 2**: Docker Hub (Team / Pro Subscription).

---

## 4. Decision Outcome

**Chosen Option: Option 1 — Amazon Elastic Container Registry (ECR)**

We selected **Amazon ECR** as the primary image registry.

### Technical Comparison

| Evaluation Dimension | Amazon ECR (Selected) | Docker Hub |
| :--- | :--- | :--- |
| **Authentication** | **IAM & IRSA**: Ephemeral tokens via AWS STS. EKS worker nodes pull images automatically using instance IAM profiles. | **Static Passwords**: Requires Personal Access Tokens stored in K8s `imagePullSecrets`. |
| **Data Transfer Cost** | **FREE Intra-Region**: Pulls from EKS nodes in `ap-south-1` incur **$0.00 data transfer fee**. | **High NAT Tax**: Each pull traverses the AWS NAT Gateway ($0.045/GB NAT fee + $0.09/GB egress). |
| **Pull Rate Limits** | **No Rate Limits**: Scalable to thousands of concurrent image layer pulls. | **Strictly Throttled**: 100/200 pulls per 6 hours. Triggers `ImagePullBackOff` during autoscaling. |
| **Network Path** | **In-VPC S3 Gateway**: Traffic stays within the private AWS network backbone. | **Public Internet**: Dependent on external route stability and public CDN availability. |
| **Security Scanning**| **Automated on Push**: Amazon Inspector integration checks CVEs continuously. | **Paid Add-on**: Requires Docker Scout subscription for full coverage. |
| **Monthly Cost** | **$0.10 per GB-month**: ~10GB platform footprint costs **~$1.00/month**. | **Per-Seat Pricing**: $5–$9 per user per month + NAT Gateway transfer fees. |

---

## 5. Architectural Implementation Details

### Zero-Secret EKS Pull Configuration
By attaching the AWS-managed policy `AmazonEC2ContainerRegistryReadOnly` to the EKS Managed Node Group IAM role, worker nodes authenticate transparently to ECR:
```yaml
# Pod spec requires NO imagePullSecrets:
spec:
  containers:
    - name: gateway
      image: 112233445566.dkr.ecr.ap-south-1.amazonaws.com/twindigital/gateway:v1.2.0
```

---

## 6. Consequences

### Positive:
* **Zero Secret Leak Risk**: No long-lived registry credentials stored in Kubernetes or Git.
* **Cost Savings**: Completely eliminates NAT Gateway egress fees on image pulls and reduces registry subscription expenses.
* **High Autoscaling Resilience**: Gateway and simulation services scale to peak capacity without `429 Too Many Requests` errors.

### Negative / Trade-offs:
* Pushing images from local developer workstations requires running `aws ecr get-login-password` via the AWS CLI.
