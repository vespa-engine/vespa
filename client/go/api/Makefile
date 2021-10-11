##@ Test
test: ## Run unit-tests with coverage.
	@echo "\033[2m→ Running unit tests...\033[0m"
	go test -timeout 30s --cover `go list ./...`

lint: ## Run linters only.
	@echo "\033[2m→ Running linters...\033[0m"
	golangci-lint run --config .golangci.yml

help:  ## Display help
	@awk 'BEGIN {FS = ":.*##"; printf "Usage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)
#------------- <https://suva.sh/posts/well-documented-makefiles> --------------


.DEFAULT_GOAL := help
.PHONY: build test integration help
