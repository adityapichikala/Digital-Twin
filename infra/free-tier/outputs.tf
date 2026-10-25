output "instance_id" {
  description = "The ID of the provisioned EC2 instance"
  value       = aws_instance.server.id
}

output "instance_public_ip" {
  description = "Public IPv4 address of the EC2 instance"
  value       = aws_instance.server.public_ip
}

output "instance_public_dns" {
  description = "Public DNS hostname of the EC2 instance"
  value       = aws_instance.server.public_dns
}

output "ssh_connection_command" {
  description = "SSH command to connect to the EC2 server"
  value       = var.key_name != "" ? "ssh -i ${var.key_name}.pem ubuntu@${aws_instance.server.public_ip}" : "ssh ubuntu@${aws_instance.server.public_ip}"
}

output "frontend_url" {
  description = "URL to access the TwinDigital Frontend Web UI"
  value       = "http://${aws_instance.server.public_ip}:8080"
}

output "gateway_url" {
  description = "URL to access the API Gateway & Metrics endpoint"
  value       = "http://${aws_instance.server.public_ip}:3000"
}

output "prometheus_url" {
  description = "URL to access the Prometheus Metrics Dashboard"
  value       = "http://${aws_instance.server.public_ip}:9090"
}

output "grafana_url" {
  description = "URL to access Grafana Dashboards"
  value       = "http://${aws_instance.server.public_ip}:3001"
}

output "vpc_id" {
  description = "ID of the provisioned VPC"
  value       = aws_vpc.main.id
}

output "security_group_id" {
  description = "ID of the provisioned Security Group"
  value       = aws_security_group.server_sg.id
}
