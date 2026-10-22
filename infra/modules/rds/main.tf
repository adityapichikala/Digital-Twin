# ============================================================
# Module: rds
#
# Provisions:
#   • RDS PostgreSQL 15 on db.t3.micro in private subnets
#   • DB Subnet Group spanning both private subnets
#   • Security Group allowing inbound 5432 ONLY from EKS nodes
#   • Random 32-char password stored in AWS Secrets Manager
#   • Secrets Manager secret for the connection string
#
# Cost estimates (ap-south-1):
#   • db.t3.micro:          ~$13/month  (on-demand, Single-AZ)
#   • 20 GiB gp3 storage:  ~$2.30/month
#   • Secrets Manager:      ~$0.40/month per secret
#   • Total:                ~$16/month
#
# ⚠️  Single-AZ = no automatic failover. Acceptable for dev.
#     For prod, set multi_az = true (+$13/month).
# ============================================================

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

# ── Random password (never written to state in plaintext) ─────
# The random_password value IS stored in tfstate — ensure the
# S3 backend bucket has encryption + restricted access.
resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%^&*()-_=+[]{}|;:,.<>?"
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
}

# ── Secrets Manager secret ────────────────────────────────────
resource "aws_secretsmanager_secret" "db" {
  name                    = "${var.cluster_name}/rds/${var.db_name}"
  description             = "TwinDigital RDS PostgreSQL credentials for ${var.db_name}"
  recovery_window_in_days = 7 # 0 for immediate deletion in dev teardown
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.db.result
    dbname   = var.db_name
    database = var.db_name
    host     = aws_db_instance.main.address
    port     = tostring(aws_db_instance.main.port)
    # Full JDBC URL for Spring Boot services
    jdbc_url = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
  })

  depends_on = [aws_db_instance.main]
}

# ── DB Subnet Group ───────────────────────────────────────────
resource "aws_db_subnet_group" "main" {
  name        = "${var.cluster_name}-rds-subnet-group"
  description = "Private subnets for TwinDigital RDS"
  subnet_ids  = var.private_subnet_ids

  tags = { Name = "${var.cluster_name}-rds-subnet-group" }
}

# ── RDS Security Group ────────────────────────────────────────
resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds-sg"
  description = "Allow PostgreSQL access from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description = "PostgreSQL from EKS worker nodes"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    # Reference the node SG — not CIDRs — so no IP changes break this
    security_groups = [var.eks_node_security_group_id]
  }

  egress {
    description = "No outbound needed for RDS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.cluster_name}-rds-sg" }
}

# ── RDS Parameter Group ───────────────────────────────────────
resource "aws_db_parameter_group" "main" {
  name        = "${var.cluster_name}-pg15"
  family      = "postgres15"
  description = "TwinDigital custom parameter group for Postgres 15"

  parameter {
    name  = "log_connections"
    value = "1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # Log queries slower than 1s
  }
  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  tags = { Name = "${var.cluster_name}-pg15" }
}

# ── RDS Instance ──────────────────────────────────────────────
resource "aws_db_instance" "main" {
  identifier = "${var.cluster_name}-postgres"

  # Engine
  engine               = "postgres"
  engine_version       = "15.5"
  instance_class       = var.db_instance_class
  parameter_group_name = aws_db_parameter_group.main.name

  # Storage — gp3 is newer/cheaper than gp2
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage # Auto-scaling ceiling
  storage_type          = "gp3"
  storage_encrypted     = true # AES-256, no extra cost

  # Credentials
  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false # Private only

  # High Availability — Single-AZ for dev
  multi_az = var.multi_az

  # Backups — 7 day retention; window outside business hours (IST)
  backup_retention_period = 7
  backup_window           = "18:30-19:30" # 00:00-01:00 IST
  maintenance_window      = "sun:19:30-sun:20:30"

  # Monitoring
  monitoring_interval = 60 # Enhanced monitoring every 60s (~$0/month for dev)
  monitoring_role_arn = aws_iam_role.rds_enhanced_monitoring.arn

  # Performance Insights (free tier: 7-day retention)
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  # Protection
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.cluster_name}-final-snapshot"

  apply_immediately = true # In dev it's fine; set false for prod

  tags = { Name = "${var.cluster_name}-postgres" }
}

# ── Enhanced Monitoring IAM Role ──────────────────────────────
resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "${var.cluster_name}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  role       = aws_iam_role.rds_enhanced_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
