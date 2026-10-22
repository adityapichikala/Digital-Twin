variable "cluster_name" {
  description = "Prefix used for all Redis resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the ElastiCache subnet group"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "EKS cluster / node security group ID allowed to reach port 6379"
  type        = string
}

variable "node_type" {
  description = "ElastiCache node instance class"
  type        = string
  default     = "cache.t4g.micro"
}

variable "num_cache_clusters" {
  description = "Number of cache clusters (1 for dev Single-Node, >=2 for Multi-AZ)"
  type        = number
  default     = 1
}

variable "parameter_group_family" {
  description = "ElastiCache parameter group family"
  type        = string
  default     = "redis7"
}

variable "engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.1"
}

variable "transit_encryption_enabled" {
  description = "Enable transit encryption (TLS)"
  type        = bool
  default     = false
}

variable "at_rest_encryption_enabled" {
  description = "Enable encryption at rest"
  type        = bool
  default     = true
}

variable "auth_token" {
  description = "Redis AUTH password token (requires transit_encryption_enabled = true)"
  type        = string
  sensitive   = true
  default     = null
}
