# ADR-001: Infrastructure as Code — HashiCorp Terraform vs. AWS CloudFormation

* **Status**: `Accepted`
* **Date**: October 2026
* **Deciders**: Architecture Team, Cloud Platform Engineering

---

## 1. Context and Problem Statement

The TwinDigital platform requires an automated, reproducible Infrastructure as Code (IaC) tool to provision and manage its entire AWS production environment:
1. Multi-AZ VPC with public and private subnets, NAT Gateways, and route tables.
2. Managed Amazon Elastic Kubernetes Service (EKS) cluster with mixed-instance managed node groups (t3.medium / t3.large).
3. Amazon RDS PostgreSQL 15 database instance for relational state.
4. Amazon ElastiCache for Redis cluster for distributed rate limiting and token cache.
5. AWS Secrets Manager resources and KMS Customer Master Keys (CMK).
6. EKS IAM OpenID Connect (OIDC) provider and least-privilege IAM Roles for Service Accounts (IRSA).
7. Kubernetes-level resources (bootstrap Helm charts, SecretStores, and namespaces).

We evaluated **HashiCorp Terraform** against **AWS CloudFormation** (including AWS CDK).

---

## 2. Decision Drivers

* **Multi-Provider Unification**: Need to provision AWS cloud resources *and* bootstrap Kubernetes manifests/Helm charts within a single, cohesive workflow.
* **Deterministic Execution Planning**: Strict requirement for previewing precise infrastructure diffs (`terraform plan`) prior to mutating live infrastructure.
* **State Locking & Drift Detection**: Need robust state tracking with distributed locking to prevent concurrent deployment collisions in team environments.
* **Modularity & Reusability**: Componentized architecture allowing clean separation of networking, compute, storage, and security layers.
* **Vendor Agnosticism & Ecosystem**: Compatibility with third-party tools (Infracost, tfsec, Trivy IaC scanning).

---

## 3. Considered Options

* **Option 1**: HashiCorp Terraform (HCL) with S3 Remote Backend and DynamoDB Locking.
* **Option 2**: AWS CloudFormation (JSON/YAML templates).
* **Option 3**: AWS Cloud Development Kit (AWS CDK in TypeScript).

---

## 4. Decision Outcome

**Chosen Option: Option 1 — HashiCorp Terraform**

We selected **Terraform** as the primary IaC engine for TwinDigital.

### Justification & Comparison

| Comparison Dimension | HashiCorp Terraform (Selected) | AWS CloudFormation |
| :--- | :--- | :--- |
| **Execution Safety** | **Deterministic `terraform plan`**: Shows exact additions, modifications, and in-place replacements before applying. | **Change Sets**: Clunky to inspect, does not always reveal cascade deletions or replacement triggers clearly. |
| **Provider Extensibility** | **Unified Multi-Provider**: Manages AWS resources (`aws`), Kubernetes (`kubernetes`), Helm (`helm`), and encryption keys (`kms`) in one state. | **AWS Only**: Cannot natively orchestrate in-cluster Kubernetes configurations without complex custom Lambda resources. |
| **Ecosystem & Modules** | Rich battle-tested modules (e.g. `terraform-aws-modules/eks`, `terraform-aws-modules/vpc`). | Basic AWS QuickStarts, frequently lagging behind new EKS/Kubernetes release features. |
| **Portability** | Universal declarative syntax across multi-cloud environments. | Locked exclusively into AWS proprietary schema. |
| **Static Security Scanning** | Seamless integration with `tfsec`, `trivy config`, and `checkov` in CI pipelines. | Limited linting tools (`cfn-lint`), harder to integrate with modern shift-left tooling. |

---

## 5. Consequences

### Positive:
* **Pre-deployment Verification**: Engineers can inspect exact resource graphs and cost deltas via `terraform plan` before committing modifications.
* **Seamless IRSA Configuration**: The `aws_iam_role` and `aws_iam_policy_document` data sources cleanly model OIDC federated trust relationships with concise condition blocks.
* **Speed of Execution**: Terraform applies changes concurrently across the directed acyclic graph (DAG) of resources, cutting infrastructure provisioning time by up to 50% compared to CloudFormation stack rollouts.

### Negative / Trade-offs:
* **State Management Overhead**: Requires bootstrapping an S3 bucket and DynamoDB table for remote state and distributed locking before the first apply.
* **State Drift Remediation**: If manual changes are made in the AWS Console, `terraform refresh` or import is required to reconcile state drift.
