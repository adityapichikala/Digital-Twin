variable "cluster_name" {
  description = "EKS cluster name — used in IAM roles, SGs, and tags"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS control plane"
  type        = string
  default     = "1.29"
}

variable "vpc_id" {
  description = "VPC ID where the cluster will be created"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for EKS nodes (at least 2 for multi-AZ)"
  type        = list(string)
}

variable "node_instance_type" {
  description = "EC2 instance type for the managed node group"
  type        = string
  default     = "t3.medium"
  # t3.medium: 2 vCPU, 4 GiB RAM — adequate for running 5 Spring Boot services
}

variable "spot_enabled" {
  description = "Use Spot instances for the node group (saves ~70%)"
  type        = bool
  default     = false
  # ⚠️  Spot instances can be interrupted. Suitable for dev/staging.
  # For production, set to false or use a mixed policy via launch template.
}

variable "node_desired_count" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "node_min_count" {
  description = "Minimum number of worker nodes (for Cluster Autoscaler)"
  type        = number
  default     = 2
}

variable "node_max_count" {
  description = "Maximum number of worker nodes (for Cluster Autoscaler)"
  type        = number
  default     = 4
}

variable "node_disk_size_gb" {
  description = "Root EBS volume size in GiB per node"
  type        = number
  default     = 20
}

variable "endpoint_public_access" {
  description = "Whether the EKS API server is publicly accessible (for dev convenience)"
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "CIDRs allowed to reach the public EKS API endpoint. Restrict to your office/VPN IP in production."
  type        = list(string)
}
