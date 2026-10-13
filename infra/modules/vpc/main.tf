# ============================================================
# Module: vpc
#
# Creates a production-ready VPC spread across 2 Availability
# Zones with the following topology:
#
#   AZ-a                           AZ-b
#   ┌──────────────────────────────────────────┐
#   │ public-a (10.0.0.0/24)  public-b (10.0.1.0/24)  │
#   │       ↕ IGW                               │
#   │ private-a(10.0.10.0/23) private-b(10.0.12.0/23)│
#   │       ↕ single NAT GW (cost-optimised)    │
#   └──────────────────────────────────────────┘
#
# Cost notes:
#   • Single NAT Gateway: ~$32/month + $0.045/GB data processed
#     (Two NAT GWs for full HA would double the fixed cost to ~$64/m)
#   • VPC, subnets, route tables, IGW: FREE
# ============================================================

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
}

# ── Data Sources ──────────────────────────────────────────────
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  az_a = data.aws_availability_zones.available.names[0]
  az_b = data.aws_availability_zones.available.names[1]
}

# ── VPC ───────────────────────────────────────────────────────
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true # Required for EKS + IRSA

  tags = {
    Name = "${var.cluster_name}-vpc"
    # EKS tags — required for the cloud controller to discover the VPC
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# ── Internet Gateway ──────────────────────────────────────────
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.cluster_name}-igw" }
}

# ── Public Subnets ────────────────────────────────────────────
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[0]
  availability_zone       = local.az_a
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.cluster_name}-public-${local.az_a}"
    # EKS tag: allows EKS to provision internet-facing LBs here
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[1]
  availability_zone       = local.az_b
  map_public_ip_on_launch = true

  tags = {
    Name                                        = "${var.cluster_name}-public-${local.az_b}"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# ── Private Subnets ───────────────────────────────────────────
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[0]
  availability_zone = local.az_a

  tags = {
    Name = "${var.cluster_name}-private-${local.az_a}"
    # EKS tag: allows EKS to provision internal LBs here
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[1]
  availability_zone = local.az_b

  tags = {
    Name                                        = "${var.cluster_name}-private-${local.az_b}"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# ── NAT Gateway (single, cost-optimised) ─────────────────────
# One EIP + one NAT GW in AZ-a.
# Trade-off: if AZ-a goes down, outbound internet from AZ-b stops.
# For HA, duplicate these for AZ-b and add var.single_nat_gateway toggle.
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "${var.cluster_name}-nat-eip" }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public_a.id # NAT lives in a PUBLIC subnet
  depends_on    = [aws_internet_gateway.main]
  tags          = { Name = "${var.cluster_name}-nat" }
}

# ── Route Tables ──────────────────────────────────────────────
# Public: → IGW
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "${var.cluster_name}-rt-public" }
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# Private: → NAT GW (both AZs share the single NAT)
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
  tags = { Name = "${var.cluster_name}-rt-private" }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

# ── VPC Flow Logs (security best practice) ───────────────────
# Cost: $0.50/GB ingested into CloudWatch (minimal for dev)
resource "aws_cloudwatch_log_group" "flow_logs" {
  name              = "/aws/vpc/${var.cluster_name}/flow-logs"
  retention_in_days = 7 # Keep costs low for dev
}

resource "aws_iam_role" "flow_logs" {
  name = "${var.cluster_name}-vpc-flow-logs-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "vpc-flow-logs.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "flow_logs" {
  name = "flow-logs-policy"
  role = aws_iam_role.flow_logs.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Resource = "*"
    }]
  })
}

resource "aws_flow_log" "main" {
  iam_role_arn    = aws_iam_role.flow_logs.arn
  log_destination = aws_cloudwatch_log_group.flow_logs.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.main.id
  tags            = { Name = "${var.cluster_name}-flow-logs" }
}
