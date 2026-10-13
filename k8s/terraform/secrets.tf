# ==============================================================================
# AWS Secrets Manager Resources for TwinDigital Production
# Secrets consumed by the External Secrets Operator (ESO)
# ==============================================================================

resource "aws_kms_key" "secrets_key" {
  description             = "KMS CMK for TwinDigital production secrets"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = {
    Project   = "twindigital"
    ManagedBy = "terraform"
  }
}

# 1. PostgreSQL Database Credentials Secret
resource "aws_secretsmanager_secret" "postgres_credentials" {
  name                    = "twindigital/production/postgres"
  description             = "Production PostgreSQL database connection credentials"
  kms_key_id              = aws_kms_key.secrets_key.arn
  recovery_window_in_days = 7

  tags = {
    Project = "twindigital"
    Service = "database"
  }
}

resource "aws_secretsmanager_secret_version" "postgres_credentials_val" {
  secret_id = aws_secretsmanager_secret.postgres_credentials.id
  secret_string = jsonencode({
    username = "twindigital_admin"
    password = "ReplaceWithStrongRandomSecretKey123!"
    database = "twindigital"
    host     = "postgres.twindigital.svc.cluster.local"
    port     = "5432"
  })
}

# 2. JWT Signing Secret
resource "aws_secretsmanager_secret" "jwt_secrets" {
  name                    = "twindigital/production/jwt"
  description             = "JWT secret key and expiration token parameters"
  kms_key_id              = aws_kms_key.secrets_key.arn
  recovery_window_in_days = 7

  tags = {
    Project = "twindigital"
    Service = "auth-gateway"
  }
}

resource "aws_secretsmanager_secret_version" "jwt_secrets_val" {
  secret_id = aws_secretsmanager_secret.jwt_secrets.id
  secret_string = jsonencode({
    secret        = "twindigital_production_jwt_super_secret_key_minimum_256_bits"
    expirationMs  = "3600000"
  })
}

# 3. Redis Cache Secret
resource "aws_secretsmanager_secret" "redis_credentials" {
  name                    = "twindigital/production/redis"
  description             = "Redis cache auth credentials"
  kms_key_id              = aws_kms_key.secrets_key.arn
  recovery_window_in_days = 7

  tags = {
    Project = "twindigital"
    Service = "cache"
  }
}

resource "aws_secretsmanager_secret_version" "redis_credentials_val" {
  secret_id = aws_secretsmanager_secret.redis_credentials.id
  secret_string = jsonencode({
    password = "ReplaceWithStrongRedisPassword456!"
    url      = "redis://:ReplaceWithStrongRedisPassword456!@redis.twindigital.svc.cluster.local:6379"
  })
}
