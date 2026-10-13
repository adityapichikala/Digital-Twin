# ============================================================
# Bootstrap — creates the S3 bucket and DynamoDB table that
# every other Terraform workspace uses for remote state.
#
# Run this ONCE before running any other `terraform` commands:
#   cd infra/bootstrap
#   terraform init
#   terraform apply
#
# ⚠️  Do NOT add this to CI/CD — it is a one-shot prerequisite.
# ============================================================

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
  # Bootstrap itself uses LOCAL state (the chicken-and-egg problem).
  # Commit the resulting terraform.tfstate to a secure location
  # (e.g. an encrypted private S3 bucket) or keep it off-repo.
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "twindigital"
      ManagedBy   = "terraform-bootstrap"
      Environment = "shared"
    }
  }
}

# ── S3 bucket for Terraform state ────────────────────────────
resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name

  # Prevent accidental deletion — set to false only when tearing down
  # Cost: ~$0.023/GB/month (negligible for tfstate files)
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle: keep last 30 non-current versions; delete older ones
resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# ── DynamoDB table for state locking ─────────────────────────
# Cost: $0 (on-demand, only billed for actual reads/writes;
#        tfstate locking is minimal I/O → effectively free)
resource "aws_dynamodb_table" "tflock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  lifecycle {
    prevent_destroy = true
  }
}
