output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "List of public subnet IDs [az-a, az-b]"
  value       = [aws_subnet.public_a.id, aws_subnet.public_b.id]
}

output "private_subnet_ids" {
  description = "List of private subnet IDs [az-a, az-b] — used by EKS nodes and RDS"
  value       = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

output "nat_gateway_ip" {
  description = "Public IP of the NAT gateway — add to allow-lists if needed"
  value       = aws_eip.nat.public_ip
}

output "availability_zones" {
  description = "AZs used by the VPC"
  value       = [local.az_a, local.az_b]
}
