# ============================================================
# Module: eks
#
# Provisions:
#   • EKS control plane (Kubernetes 1.29, private API optional)
#   • IAM role for the control plane
#   • Managed Node Group (t3.medium × 2, with spot toggle)
#   • IAM role for node group + standard managed policies
#   • OIDC Identity Provider (prerequisite for IRSA)
#   • aws-auth ConfigMap update via aws_eks_access_entry (new API)
#
# Cost estimates (ap-south-1, on-demand):
#   • EKS control plane:  $0.10/hour  →  ~$72/month
#   • 2× t3.medium OD:   $0.0416/hr  →  ~$60/month total
#   • 2× t3.medium Spot:              →  ~$18/month total (70% saving)
#   • EBS (20 GiB × 2):  $0.08/GB/m  →  ~$3.20/month
#
# Total dev estimate: ~$75–$133/month depending on Spot availability
# ============================================================

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

# ── Data Sources ──────────────────────────────────────────────
data "aws_caller_identity" "current" {}

# ── Control Plane IAM Role ────────────────────────────────────
resource "aws_iam_role" "cluster" {
  name = "${var.cluster_name}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role_policy_attachment" "cluster_vpc_resource" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
}

# ── EKS Cluster Security Group ────────────────────────────────
# No cross-SG inline rules here — those are added as separate
# aws_security_group_rule resources below to break the cycle.
resource "aws_security_group" "cluster" {
  name        = "${var.cluster_name}-cluster-sg"
  description = "EKS cluster control-plane security group"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.cluster_name}-cluster-sg" }
}

# ── EKS Cluster ───────────────────────────────────────────────
resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    security_group_ids      = [aws_security_group.cluster.id]
    endpoint_private_access = true
    # Public access for kubectl convenience in dev.
    # Set to false in production and use a bastion/VPN.
    endpoint_public_access = var.endpoint_public_access
    public_access_cidrs    = var.public_access_cidrs
  }

  # Enable control-plane logging — stored in CloudWatch
  # Cost: minimal for dev (a few MB/day)
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_iam_role_policy_attachment.cluster_vpc_resource,
  ]

  tags = { Name = var.cluster_name }
}

# ── OIDC Provider (prerequisite for IRSA) ────────────────────
# The TLS certificate thumbprint is fetched from the issuer URL.
data "tls_certificate" "cluster" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "cluster" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.cluster.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

# ── Node Group IAM Role ───────────────────────────────────────
resource "aws_iam_role" "node" {
  name = "${var.cluster_name}-eks-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node_worker" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr" {
  role = aws_iam_role.node.name
  # ReadOnly — nodes only pull images, never push
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# SSM access so you can shell into nodes without opening SSH
resource "aws_iam_role_policy_attachment" "node_ssm" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ── Node Security Group ───────────────────────────────────────
# Cross-SG ingress rules are defined as separate resources below.
resource "aws_security_group" "node" {
  name        = "${var.cluster_name}-node-sg"
  description = "EKS managed node group security group"
  vpc_id      = var.vpc_id

  # Full intra-node communication (pods to pods across nodes)
  ingress {
    description = "Node-to-node all traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.cluster_name}-node-sg" }
}

# ── Cross-SG Rules (defined AFTER both SGs exist) ─────────────
# Rule 1: nodes → cluster API server (port 443)
resource "aws_security_group_rule" "nodes_to_cluster_api" {
  type                     = "ingress"
  description              = "Allow nodes to reach EKS API server"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.cluster.id
  source_security_group_id = aws_security_group.node.id
}

# Rule 2: cluster → nodes kubelet (port 10250)
resource "aws_security_group_rule" "cluster_to_nodes_kubelet" {
  type                     = "ingress"
  description              = "Allow control plane to reach kubelet on nodes"
  from_port                = 10250
  to_port                  = 10250
  protocol                 = "tcp"
  security_group_id        = aws_security_group.node.id
  source_security_group_id = aws_security_group.cluster.id
}

# Rule 3: cluster → nodes webhook ports (e.g. admission webhooks)
resource "aws_security_group_rule" "cluster_to_nodes_webhooks" {
  type                     = "ingress"
  description              = "Allow control plane to reach node webhook ports"
  from_port                = 8443
  to_port                  = 8443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.node.id
  source_security_group_id = aws_security_group.cluster.id
}

# ── Managed Node Group ────────────────────────────────────────
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-ng-main"
  node_role_arn   = aws_iam_role.node.arn
  # Deploy nodes into private subnets only
  subnet_ids = var.private_subnet_ids

  instance_types = [var.node_instance_type]

  # Spot configuration: if spot_enabled, use SPOT; else ON_DEMAND
  capacity_type = var.spot_enabled ? "SPOT" : "ON_DEMAND"

  scaling_config {
    desired_size = var.node_desired_count
    min_size     = var.node_min_count
    max_size     = var.node_max_count
  }

  update_config {
    # Roll one node at a time to avoid downtime
    max_unavailable = 1
  }

  disk_size = var.node_disk_size_gb

  # Force update when the launch template changes
  force_update_version = false

  labels = {
    role        = "worker"
    environment = "dev"
  }

  tags = {
    Name = "${var.cluster_name}-ng-main"
    # Required for Cluster Autoscaler to discover this node group
    "k8s.io/cluster-autoscaler/enabled"             = "true"
    "k8s.io/cluster-autoscaler/${var.cluster_name}" = "owned"
  }

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr,
  ]

  lifecycle {
    # Prevent Terraform from destroying nodes if auto-scaler changes desired count
    ignore_changes = [scaling_config[0].desired_size]
  }
}

# ── CloudWatch Log Group for control-plane logs ───────────────
resource "aws_cloudwatch_log_group" "eks" {
  name              = "/aws/eks/${var.cluster_name}/cluster"
  retention_in_days = 7 # 7 days is sufficient for dev; increase for prod

  lifecycle {
    prevent_destroy = false
  }
}
