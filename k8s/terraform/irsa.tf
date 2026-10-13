# ==============================================================================
# AWS IAM Roles for Service Accounts (IRSA) - Terraform Snippets
# Least-privilege IAM isolation for cost-risk-service & external-secrets
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

variable "aws_region" {
  type        = string
  default     = "ap-south-1"
  description = "AWS deployment region"
}

variable "aws_account_id" {
  type        = string
  default     = "112233445566"
  description = "Target AWS Account ID"
}

variable "cluster_name" {
  type        = string
  default     = "twindigital-cluster"
  description = "EKS Cluster Name"
}

variable "oidc_provider_url" {
  type        = string
  default     = "oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLEOIDC0011223344"
  description = "EKS OpenID Connect provider URL without https://"
}

variable "oidc_provider_arn" {
  type        = string
  default     = "arn:aws:iam::112233445566:oidc-provider/oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLEOIDC0011223344"
  description = "EKS OpenID Connect provider IAM ARN"
}

# ──────────────────────────────────────────────────────────────────────────────
# 1. COST-RISK-SERVICE IRSA ROLE
# Strict least-privilege: ONLY Cost Explorer & Pricing APIs allowed.
# ──────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "cost_risk_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:twindigital:cost-risk-service-sa"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cost_risk_role" {
  name                 = "twindigital-cost-risk-irsa"
  assume_role_policy   = data.aws_iam_policy_document.cost_risk_assume_role.json
  description          = "Least-privilege IRSA role for cost-risk-service with Cost Explorer read-only access"

  tags = {
    Project     = "twindigital"
    Service     = "cost-risk-service"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# IAM Policy: Cost Explorer & AWS Pricing read-only, explicit DENY on all other AWS services
data "aws_iam_policy_document" "cost_risk_policy" {
  # Statement 1: Read-only Cost Explorer queries
  statement {
    sid    = "AllowCostExplorerRead"
    effect = "Allow"
    actions = [
      "ce:GetCostAndUsage",
      "ce:GetCostForecast",
      "ce:GetDimensionValues",
      "ce:GetTags",
      "pricing:GetProducts",
      "pricing:DescribeServices"
    ]
    resources = ["*"]
  }

  # Statement 2: Explicit DENY for any other AWS services/mutations
  statement {
    sid       = "DenyAllOtherAwsServices"
    effect    = "Deny"
    not_actions = [
      "ce:GetCostAndUsage",
      "ce:GetCostForecast",
      "ce:GetDimensionValues",
      "ce:GetTags",
      "pricing:GetProducts",
      "pricing:DescribeServices"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "cost_risk_policy" {
  name        = "twindigital-cost-risk-policy"
  description = "Grants strict read-only access to AWS Cost Explorer and Pricing APIs"
  policy      = data.aws_iam_policy_document.cost_risk_policy.json
}

resource "aws_iam_role_policy_attachment" "cost_risk_attachment" {
  role       = aws_iam_role.cost_risk_role.name
  policy_arn = aws_iam_policy.cost_risk_policy.arn
}

# ──────────────────────────────────────────────────────────────────────────────
# 2. EXTERNAL SECRETS OPERATOR IRSA ROLE
# Least-privilege: Read-only access to secrets in twindigital/production/*
# ──────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "external_secrets_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:twindigital:external-secrets-sa"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "external_secrets_role" {
  name                 = "twindigital-external-secrets-irsa"
  assume_role_policy   = data.aws_iam_policy_document.external_secrets_assume_role.json
  description          = "IRSA role for External Secrets Operator to sync AWS Secrets Manager"

  tags = {
    Project     = "twindigital"
    Service     = "external-secrets"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

data "aws_iam_policy_document" "external_secrets_policy" {
  statement {
    sid    = "AllowSecretsManagerRead"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret"
    ]
    resources = [
      "arn:aws:secretsmanager:${var.aws_region}:${var.aws_account_id}:secret:twindigital/production/*"
    ]
  }

  statement {
    sid    = "DenyMutations"
    effect = "Deny"
    actions = [
      "secretsmanager:CreateSecret",
      "secretsmanager:DeleteSecret",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecret"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "external_secrets_policy" {
  name        = "twindigital-external-secrets-policy"
  description = "Grants read-only access strictly to twindigital/production/* secrets"
  policy      = data.aws_iam_policy_document.external_secrets_policy.json
}

resource "aws_iam_role_policy_attachment" "external_secrets_attachment" {
  role       = aws_iam_role.external_secrets_role.name
  policy_arn = aws_iam_policy.external_secrets_policy.arn
}

# ──────────────────────────────────────────────────────────────────────────────
# OUTPUTS
# ──────────────────────────────────────────────────────────────────────────────

output "cost_risk_service_role_arn" {
  value       = aws_iam_role.cost_risk_role.arn
  description = "IAM Role ARN to annotate on cost-risk-service-sa"
}

output "external_secrets_role_arn" {
  value       = aws_iam_role.external_secrets_role.arn
  description = "IAM Role ARN to annotate on external-secrets-sa"
}
