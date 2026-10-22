terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
}

resource "aws_elasticache_subnet_group" "main" {
  name        = "${var.cluster_name}-redis-subnet-group"
  description = "Private subnets for TwinDigital ElastiCache Redis"
  subnet_ids  = var.private_subnet_ids

  tags = {
    Name = "${var.cluster_name}-redis-subnet-group"
  }
}

resource "aws_security_group" "redis" {
  name        = "${var.cluster_name}-redis-sg"
  description = "Allow Redis access from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from EKS cluster / worker nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  egress {
    description = "No outbound needed for Redis"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-redis-sg"
  }
}

resource "aws_elasticache_parameter_group" "main" {
  name   = "${var.cluster_name}-redis7-pg"
  family = var.parameter_group_family

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }

  tags = {
    Name = "${var.cluster_name}-redis7-pg"
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${var.cluster_name}-redis"
  description          = "TwinDigital Redis cache for rate limiting and sessions"
  node_type            = var.node_type
  num_cache_clusters   = var.num_cache_clusters
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.main.name
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  engine         = "redis"
  engine_version = var.engine_version

  at_rest_encryption_enabled = var.at_rest_encryption_enabled
  transit_encryption_enabled = var.transit_encryption_enabled
  auth_token                 = var.transit_encryption_enabled ? var.auth_token : null

  automatic_failover_enabled = var.num_cache_clusters > 1 ? true : false
  multi_az_enabled           = var.num_cache_clusters > 1 ? true : false

  apply_immediately = true

  tags = {
    Name = "${var.cluster_name}-redis"
  }
}

resource "aws_secretsmanager_secret" "redis" {
  name                    = "${var.cluster_name}/redis"
  description             = "TwinDigital Redis cache connection info"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis" {
  secret_id = aws_secretsmanager_secret.redis.id
  secret_string = jsonencode({
    host      = aws_elasticache_replication_group.main.primary_endpoint_address
    port      = "6379"
    password  = var.auth_token != null ? var.auth_token : ""
    redis_url = var.auth_token != null ? "redis://:${var.auth_token}@${aws_elasticache_replication_group.main.primary_endpoint_address}:6379" : "redis://${aws_elasticache_replication_group.main.primary_endpoint_address}:6379"
  })

  depends_on = [aws_elasticache_replication_group.main]
}
