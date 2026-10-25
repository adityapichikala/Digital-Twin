variable "aws_region" {
  description = "AWS deployment region"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name tag"
  type        = string
  default     = "twindigital"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "instance_type" {
  description = "EC2 instance type (t2.micro is AWS Free Tier eligible)"
  type        = string
  default     = "t2.micro"
}

variable "key_name" {
  description = "Name of existing AWS EC2 Key Pair for SSH access (optional)"
  type        = string
  default     = ""
}

variable "allowed_ssh_cidr" {
  description = "CIDR allowed to SSH into the instance (set to your IP/32 in production)"
  type        = string
  default     = "0.0.0.0/0"
}

variable "root_volume_size" {
  description = "Root EBS volume size in GB (AWS Free Tier allows up to 30 GB)"
  type        = number
  default     = 25
}
