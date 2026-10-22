# ── Global ────────────────────────────────────────────────────
variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment label (dev / staging / prod)"
  type        = string
  default     = "dev"
}

variable "cluster_name" {
  description = "Base name for the EKS cluster and all associated resources"
  type        = string
  default     = "twindigital-dev"
}

variable "owner_tag" {
  description = "Team or individual owning this stack (used in default_tags)"
  type        = string
  default     = "platform-team"
}

# ── VPC ───────────────────────────────────────────────────────
variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.10.0/23", "10.0.12.0/23"]
}

# ── EKS ───────────────────────────────────────────────────────
variable "kubernetes_version" {
  type    = string
  default = "1.29"
}

variable "node_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "spot_enabled" {
  description = "Use Spot instances (~70% cheaper). Acceptable for dev."
  type        = bool
  default     = true
}

variable "node_desired_count" {
  type    = number
  default = 2
}

variable "node_min_count" {
  type    = number
  default = 2
}

variable "node_max_count" {
  type    = number
  default = 4
}

variable "node_disk_size_gb" {
  type    = number
  default = 20
}

variable "endpoint_public_access" {
  description = "Allow kubectl from the public internet (restrict to VPN CIDR in prod)"
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "CIDRs allowed to reach the EKS public API endpoint"
  type        = list(string)
  # ⚠️  Specify your static office/VPN IP for security (no 0.0.0.0/0 default)
}

# ── RDS ───────────────────────────────────────────────────────
variable "db_name" {
  type    = string
  default = "twindigital"
}

variable "db_username" {
  type    = string
  default = "twindigital"
}

variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "rds_multi_az" {
  type    = bool
  default = false
}

variable "rds_deletion_protection" {
  type    = bool
  default = false
}

variable "rds_skip_final_snapshot" {
  type    = bool
  default = true
}

# ── Redis (ElastiCache) ───────────────────────────────────────
variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_num_cache_clusters" {
  description = "Number of cache clusters for Redis"
  type        = number
  default     = 1
}
