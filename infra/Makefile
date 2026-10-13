# ============================================================
# TwinDigital Infrastructure Makefile
#
# All targets operate on envs/dev by default.
# Override with: make plan ENV=staging
#
# Prerequisites:
#   - terraform >= 1.7   (brew install terraform / tfenv)
#   - aws CLI configured (aws configure / IRSA env vars)
#   - kubectl            (brew install kubectl)
#
# First-time setup:
#   make bootstrap       ← creates S3 bucket + DynamoDB table
#   (edit envs/dev/backend.tf with the output bucket name)
#   make init
#   make plan
#   make apply
# ============================================================

ENV          ?= dev
INFRA_DIR    := infra/envs/$(ENV)
BOOTSTRAP_DIR := infra/bootstrap
TF           := terraform
AWS_REGION   ?= ap-south-1

# Colour helpers (works on macOS/Linux; harmless on Windows Git Bash)
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
RESET  := \033[0m

.PHONY: help bootstrap init validate plan apply destroy \
        kubeconfig nodes fmt lint clean cost-estimate

# ── Default target ────────────────────────────────────────────
help: ## Show this help
	@echo ""
	@echo "  TwinDigital Infrastructure Commands"
	@echo "  ===================================="
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-22s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "  ENV=$(ENV)  (override: make plan ENV=staging)"
	@echo ""

# ── Bootstrap (run once) ──────────────────────────────────────
bootstrap: ## Create S3 bucket + DynamoDB table for remote state
	@echo "$(YELLOW)▶ Bootstrapping remote state for region $(AWS_REGION)$(RESET)"
	cd $(BOOTSTRAP_DIR) && \
	  $(TF) init && \
	  $(TF) apply -auto-approve
	@echo "$(GREEN)✔ Bootstrap complete. Update infra/envs/dev/backend.tf with the output bucket name.$(RESET)"

# ── Initialise ────────────────────────────────────────────────
init: ## Initialise Terraform (downloads providers and configures backend)
	@echo "$(YELLOW)▶ Initialising $(ENV) environment$(RESET)"
	cd $(INFRA_DIR) && $(TF) init -upgrade

# ── Validate ──────────────────────────────────────────────────
validate: init ## Validate Terraform configuration (no network calls)
	@echo "$(YELLOW)▶ Validating $(ENV)$(RESET)"
	cd $(INFRA_DIR) && $(TF) validate
	@echo "$(GREEN)✔ Validation passed$(RESET)"

# ── Format ────────────────────────────────────────────────────
fmt: ## Format all .tf files recursively
	$(TF) fmt -recursive infra/

# ── Lint (requires tflint) ────────────────────────────────────
lint: ## Run tflint on all modules and envs
	tflint --chdir=$(INFRA_DIR)

# ── Plan ──────────────────────────────────────────────────────
plan: init ## Show what Terraform will change (saves plan to infra/envs/ENV/tfplan)
	@echo "$(YELLOW)▶ Planning $(ENV)$(RESET)"
	cd $(INFRA_DIR) && $(TF) plan \
	  -out=tfplan \
	  -var-file=terraform.tfvars
	@echo "$(GREEN)✔ Plan saved to $(INFRA_DIR)/tfplan$(RESET)"
	@echo "  Review, then run: make apply ENV=$(ENV)"

# ── Apply ─────────────────────────────────────────────────────
apply: ## Apply the saved plan (runs plan first if no tfplan exists)
	@echo "$(YELLOW)▶ Applying $(ENV)$(RESET)"
	@if [ ! -f "$(INFRA_DIR)/tfplan" ]; then \
	  echo "No saved plan found — running plan first..."; \
	  cd $(INFRA_DIR) && $(TF) plan -out=tfplan -var-file=terraform.tfvars; \
	fi
	cd $(INFRA_DIR) && $(TF) apply tfplan
	@rm -f $(INFRA_DIR)/tfplan
	@echo "$(GREEN)✔ Apply complete for $(ENV)$(RESET)"
	@$(MAKE) kubeconfig ENV=$(ENV)

# ── Configure kubectl ─────────────────────────────────────────
kubeconfig: ## Update local kubeconfig for the EKS cluster
	@CLUSTER_NAME=$$(cd $(INFRA_DIR) && $(TF) output -raw cluster_name 2>/dev/null); \
	if [ -z "$$CLUSTER_NAME" ]; then \
	  echo "$(RED)✗ cluster_name output not found — has apply completed?$(RESET)"; exit 1; \
	fi; \
	echo "$(YELLOW)▶ Updating kubeconfig for cluster: $$CLUSTER_NAME$(RESET)"; \
	aws eks update-kubeconfig --region $(AWS_REGION) --name $$CLUSTER_NAME; \
	echo "$(GREEN)✔ kubeconfig updated$(RESET)"

# ── Verify nodes ──────────────────────────────────────────────
nodes: kubeconfig ## Verify EKS nodes are Ready
	@echo "$(YELLOW)▶ Checking EKS nodes$(RESET)"
	kubectl get nodes -o wide

# ── Outputs ───────────────────────────────────────────────────
outputs: ## Show all Terraform outputs for the current env
	cd $(INFRA_DIR) && $(TF) output

# ── Destroy ───────────────────────────────────────────────────
destroy: ## ⚠️  DESTROY all infrastructure for ENV (requires confirmation)
	@echo ""
	@echo "$(RED)╔══════════════════════════════════════════════════════════════╗$(RESET)"
	@echo "$(RED)║  ⚠️   DESTROY CHECKLIST — complete ALL steps first           ║$(RESET)"
	@echo "$(RED)╠══════════════════════════════════════════════════════════════╣$(RESET)"
	@echo "$(RED)║  [ ] 1. Drain and delete all Kubernetes workloads            ║$(RESET)"
	@echo "$(RED)║         kubectl delete all --all -A                          ║$(RESET)"
	@echo "$(RED)║  [ ] 2. Delete any PersistentVolumeClaims (releases EBS)     ║$(RESET)"
	@echo "$(RED)║         kubectl delete pvc --all -A                          ║$(RESET)"
	@echo "$(RED)║  [ ] 3. Delete all LoadBalancer Services (removes ELBs)      ║$(RESET)"
	@echo "$(RED)║         kubectl delete svc --all -A                          ║$(RESET)"
	@echo "$(RED)║  [ ] 4. Take a final RDS snapshot if you need the data       ║$(RESET)"
	@echo "$(RED)║         aws rds create-db-snapshot ...                       ║$(RESET)"
	@echo "$(RED)║  [ ] 5. Revoke any Secrets Manager access not managed by TF  ║$(RESET)"
	@echo "$(RED)║  [ ] 6. Remove the External Secrets Operator if installed    ║$(RESET)"
	@echo "$(RED)║  [ ] 7. Confirm AWS Cost Explorer shows no surprise charges  ║$(RESET)"
	@echo "$(RED)║  [ ] 8. Notify the team — this is irreversible               ║$(RESET)"
	@echo "$(RED)╚══════════════════════════════════════════════════════════════╝$(RESET)"
	@echo ""
	@read -p "  Type the environment name to confirm destruction [$(ENV)]: " confirm; \
	if [ "$$confirm" != "$(ENV)" ]; then \
	  echo "$(YELLOW)✗ Destruction cancelled.$(RESET)"; exit 1; \
	fi
	@echo "$(RED)▶ Destroying $(ENV) — this may take 20-30 minutes$(RESET)"
	cd $(INFRA_DIR) && $(TF) destroy -var-file=terraform.tfvars
	@echo "$(RED)✔ $(ENV) infrastructure destroyed$(RESET)"
	@echo ""
	@echo "$(YELLOW)Post-destroy checklist:$(RESET)"
	@echo "  [ ] Delete the S3 tfstate bucket if no longer needed:"
	@echo "        aws s3 rb s3://<bucket-name> --force"
	@echo "  [ ] Delete the DynamoDB lock table:"
	@echo "        aws dynamodb delete-table --table-name twindigital-tflock"
	@echo "  [ ] Unset local kubeconfig context:"
	@echo "        kubectl config delete-context <arn>"
	@echo "  [ ] Verify AWS Cost Explorer for lingering charges in 24h"

# ── Clean local Terraform cache ───────────────────────────────
clean: ## Remove local .terraform directories and plan files
	find infra -name ".terraform" -type d -exec rm -rf {} + 2>/dev/null; true
	find infra -name "tfplan"    -type f -delete 2>/dev/null; true
	find infra -name ".terraform.lock.hcl" -type f -delete 2>/dev/null; true
	@echo "$(GREEN)✔ Cleaned local Terraform state$(RESET)"

# ── Cost estimate (requires infracost) ───────────────────────
cost-estimate: ## Estimate monthly cost with infracost (requires `brew install infracost`)
	@command -v infracost >/dev/null 2>&1 || { \
	  echo "$(RED)infracost not found. Install: brew install infracost$(RESET)"; exit 1; }
	infracost breakdown --path $(INFRA_DIR) --format table
