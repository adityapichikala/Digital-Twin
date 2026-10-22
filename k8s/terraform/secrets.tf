# ==============================================================================
# AWS Secrets Manager Resources for TwinDigital Production
# Secrets consumed by the External Secrets Operator (ESO)
# ==============================================================================

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "twindigital_admin"
}

variable "db_password" {
  description = "Database master password (uses random generation if not provided)"
  type        = string
  sensitive   = true
  default     = null
}

variable "db_name" {
  description = "Database name"
  type        = string
  default     = "twindigital"
}

variable "db_host" {
  description = "Database host address"
  type        = string
  default     = "postgres.twindigital.svc.cluster.local"
}

variable "db_port" {
  description = "Database port"
  type        = string
  default     = "5432"
}

variable "jwt_secret" {
  description = "JWT HMAC signing secret (uses random generation if not provided)"
  type        = string
  sensitive   = true
  default     = null
}

variable "jwt_expiration_ms" {
  description = "JWT token expiration in milliseconds"
  type        = string
  default     = "3600000"
}

variable "redis_password" {
  description = "Redis password (uses random generation if not provided)"
  type        = string
  sensitive   = true
  default     = null
}

variable "redis_url" {
  description = "Redis connection URL"
  type        = string
  sensitive   = true
  default     = null
}

# ── Dynamic Passwords (when not provided via variables) ───────
resource "random_password" "db_password" {
  count            = var.db_password == null ? 1 : 0
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "jwt_secret" {
  count   = var.jwt_secret == null ? 1 : 0
  length  = 64
  special = false
}

resource "random_password" "redis_password" {
  count   = var.redis_password == null ? 1 : 0
  length  = 32
  special = false
}

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
    username = var.db_username
    password = var.db_password != null ? var.db_password : random_password.db_password[0].result
    database = var.db_name
    dbname   = var.db_name
    host     = var.db_host
    port     = var.db_port
    jdbc_url = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}"
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
    secret       = var.jwt_secret != null ? var.jwt_secret : random_password.jwt_secret[0].result
    expirationMs = var.jwt_expiration_ms
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
    password = var.redis_password != null ? var.redis_password : random_password.redis_password[0].result
    url      = var.redis_url != null ? var.redis_url : "redis://:${var.redis_password != null ? var.redis_password : random_password.redis_password[0].result}@redis.twindigital.svc.cluster.local:6379"
  })
}
