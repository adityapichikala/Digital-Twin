# ==============================================================================
# AWS Secrets Manager Resources for TwinDigital Production
# Secrets consumed by the External Secrets Operator (ESO)
# (RDS credentials managed by infra/modules/rds; ElastiCache by infra/modules/redis)
# ==============================================================================

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

# ── Dynamic Passwords (when not provided via variables) ───────
resource "random_password" "jwt_secret" {
  count   = var.jwt_secret == null ? 1 : 0
  length  = 64
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

# JWT Signing Secret (used by auth-service & gateway)
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

