# ── Cluster ───────────────────────────────────────────────────
output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server URL"
  value       = module.eks.cluster_endpoint
}

output "cluster_version" {
  description = "Kubernetes version"
  value       = module.eks.cluster_version
}

output "kubeconfig_command" {
  description = "Run this to configure kubectl for the cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "kubectl_verify_command" {
  description = "After running kubeconfig_command, verify nodes with:"
  value       = "kubectl get nodes -o wide"
}

# ── OIDC (for IRSA) ──────────────────────────────────────────
output "oidc_provider_arn" {
  description = "OIDC provider ARN — use this when creating IRSA IAM roles"
  value       = module.eks.oidc_provider_arn
}

output "oidc_provider_url" {
  description = "OIDC issuer URL (no https://) — use in IAM trust conditions"
  value       = module.eks.oidc_provider_url
}

# ── Network ───────────────────────────────────────────────────
output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnet IDs (for EKS and RDS)"
  value       = module.vpc.private_subnet_ids
}

output "nat_gateway_ip" {
  description = "NAT Gateway EIP — add to any external allow-lists"
  value       = module.vpc.nat_gateway_ip
}

# ── RDS ───────────────────────────────────────────────────────
output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (private DNS)"
  value       = module.rds.db_endpoint
}

output "rds_port" {
  description = "RDS port"
  value       = module.rds.db_port
}

output "rds_secret_arn" {
  description = "Secrets Manager ARN — use with ExternalSecretsOperator or directly in pods"
  value       = module.rds.secret_arn
}

output "rds_secret_name" {
  description = "Secrets Manager secret name — retrieve with: aws secretsmanager get-secret-value --secret-id <name>"
  value       = module.rds.secret_name
}

output "retrieve_db_secret_command" {
  description = "Command to retrieve the DB secret"
  value       = "aws secretsmanager get-secret-value --secret-id ${module.rds.secret_name} --region ${var.aws_region} --query SecretString --output text | jq ."
}
