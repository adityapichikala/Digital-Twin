output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint hostname"
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "redis_port" {
  description = "ElastiCache Redis port"
  value       = aws_elasticache_replication_group.main.port
}

output "redis_security_group_id" {
  description = "Redis security group ID"
  value       = aws_security_group.redis.id
}

output "secret_arn" {
  description = "ARN of the Secrets Manager secret containing Redis connection info"
  value       = aws_secretsmanager_secret.redis.arn
}

output "secret_name" {
  description = "Name of the Secrets Manager secret"
  value       = aws_secretsmanager_secret.redis.name
}

output "redis_url" {
  description = "Redis connection URL"
  value       = var.transit_encryption_enabled && var.auth_token != null ? "redis://:${var.auth_token}@${aws_elasticache_replication_group.main.primary_endpoint_address}:6379" : "redis://${aws_elasticache_replication_group.main.primary_endpoint_address}:6379"
  sensitive   = true
}
