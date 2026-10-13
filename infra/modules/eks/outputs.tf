output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_certificate_authority" {
  description = "Base64-encoded certificate authority data for the cluster"
  value       = aws_eks_cluster.main.certificate_authority[0].data
  sensitive   = true
}

output "cluster_version" {
  description = "Kubernetes version running on the cluster"
  value       = aws_eks_cluster.main.version
}

output "oidc_provider_arn" {
  description = "ARN of the OIDC identity provider — needed to create IRSA roles"
  value       = aws_iam_openid_connect_provider.cluster.arn
}

output "oidc_provider_url" {
  description = "OIDC issuer URL (without https://) — used in IRSA trust policies"
  value       = replace(aws_eks_cluster.main.identity[0].oidc[0].issuer, "https://", "")
}

output "node_role_arn" {
  description = "IAM role ARN for EKS worker nodes"
  value       = aws_iam_role.node.arn
}

output "node_security_group_id" {
  description = "Security group ID attached to worker nodes — used by RDS module"
  value       = aws_security_group.node.id
}

output "kubeconfig_command" {
  description = "Run this command to update your local kubeconfig"
  # Extract region from the cluster ARN: arn:aws:eks:<region>:<account>:cluster/<name>
  value = "aws eks update-kubeconfig --region ${split(":", aws_eks_cluster.main.arn)[3]} --name ${aws_eks_cluster.main.name}"
}
