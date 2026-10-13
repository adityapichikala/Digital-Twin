variable "aws_region" {
  description = "AWS region for the remote-state infrastructure"
  type        = string
  default     = "ap-south-1"
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name for Terraform state. Must be lowercase, no underscores."
  type        = string
  # Override in terraform.tfvars — do NOT use the default in production
  default = "twindigital-tfstate-CHANGEME"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$", var.state_bucket_name))
    error_message = "Bucket name must be 3-63 chars, lowercase, alphanumeric and hyphens only."
  }
}

variable "lock_table_name" {
  description = "DynamoDB table name for Terraform state locking"
  type        = string
  default     = "twindigital-tflock"
}
