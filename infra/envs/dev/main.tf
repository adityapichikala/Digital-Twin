# ============================================================
# envs/dev — Root module composing VPC + EKS + RDS
#
# Relationships:
#   vpc  →  (exports subnet IDs)
#   eks  →  consumes vpc.private_subnet_ids, exports node_sg_id
#   rds  →  consumes vpc.private_subnet_ids + eks.node_security_group_id
# ============================================================

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "twindigital"
      Environment = var.environment
      ManagedBy   = "terraform"
      Owner       = var.owner_tag
    }
  }
}

# ── VPC ───────────────────────────────────────────────────────
module "vpc" {
  source = "../../modules/vpc"

  cluster_name         = var.cluster_name
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}

# ── EKS ───────────────────────────────────────────────────────
module "eks" {
  source = "../../modules/eks"

  cluster_name       = var.cluster_name
  kubernetes_version = var.kubernetes_version
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids

  node_instance_type = var.node_instance_type
  spot_enabled       = var.spot_enabled
  node_desired_count = var.node_desired_count
  node_min_count     = var.node_min_count
  node_max_count     = var.node_max_count
  node_disk_size_gb  = var.node_disk_size_gb

  endpoint_public_access = var.endpoint_public_access
  public_access_cidrs    = var.public_access_cidrs
}

# ── RDS ───────────────────────────────────────────────────────
module "rds" {
  source = "../../modules/rds"

  cluster_name               = var.cluster_name
  vpc_id                     = module.vpc.vpc_id
  private_subnet_ids         = module.vpc.private_subnet_ids
  eks_node_security_group_id = module.eks.cluster_security_group_id

  db_name             = var.db_name
  db_username         = var.db_username
  db_instance_class   = var.db_instance_class
  multi_az            = var.rds_multi_az
  deletion_protection = var.rds_deletion_protection
  skip_final_snapshot = var.rds_skip_final_snapshot
}

# ── Redis (ElastiCache) ───────────────────────────────────────
module "redis" {
  source = "../../modules/redis"

  cluster_name          = var.cluster_name
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id

  node_type          = var.redis_node_type
  num_cache_clusters = var.redis_num_cache_clusters
}

