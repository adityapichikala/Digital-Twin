# ============================================================
# Remote State Backend — envs/dev
#
# Run `cd infra/bootstrap && terraform apply` first, then copy
# the output values into this file.
# ============================================================

terraform {
  backend "s3" {
    bucket         = "twindigital-tfstate-CHANGEME" # ← from bootstrap output
    key            = "dev/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "twindigital-tflock"
    encrypt        = true
  }
}
