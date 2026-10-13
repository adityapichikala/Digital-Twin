variable "cluster_name" {
  description = "Prefix used for all RDS resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the DB subnet group (min 2 for multi-AZ readiness)"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "EKS node security group ID — the only source allowed to reach port 5432"
  type        = string
}

variable "db_name" {
  description = "Initial database name to create"
  type        = string
  default     = "twindigital"
}

variable "db_username" {
  description = "Master database username"
  type        = string
  default     = "twindigital"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
  # db.t3.micro: 1 vCPU, 1 GiB RAM (~$13/month in ap-south-1)
  # Upgrade to db.t3.small for production workloads
}

variable "db_allocated_storage" {
  description = "Initial storage in GiB"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum storage in GiB for autoscaling (0 = disabled)"
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Enable Multi-AZ for automatic failover (doubles cost)"
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Prevent accidental deletion of the RDS instance"
  type        = bool
  default     = false
  # Set to true in production
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot on destroy (safe for dev, NOT for prod)"
  type        = bool
  default     = true
}
