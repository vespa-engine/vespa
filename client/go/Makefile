# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# The version to release. Defaults to the current tag or revision.
# Use env VERSION=X.Y.Z make ... to override
VERSION ?= $(shell git describe --tags --exact-match 2> /dev/null | sed "s/^v//")
DEVEL_VERSION := $(shell echo "0.0.0-`git rev-parse --short HEAD`")
ifeq ($(VERSION),)
	VERSION = $(DEVEL_VERSION)
endif

PREFIX ?= $(CURDIR)
BIN ?= $(PREFIX)/bin
SHARE ?= $(PREFIX)/share
DIST ?= $(CURDIR)/dist

GO_FLAGS := -ldflags "-X github.com/vespa-engine/vespa/client/go/internal/build.Version=$(VERSION)"
GIT_ROOT := $(shell git rev-parse --show-toplevel)
DIST_TARGETS := dist-mac dist-mac-arm64 dist-linux dist-linux-arm64 dist-win32 dist-win64

all: test checkfmt install

#
# Dist targets
#

# Bump the version of the vespa-cli formula and create a pull request to Homebrew repository.
#
# Homebrew's automated BrewTestBot must do the merge for the bottles to be published. When
# the PR has been merged check that the merge was done by their bot, and that installing
# the new version works by running: `brew update && brew install vespa-cli`.
#
# Example:
#
# $ make dist-homebrew
--dist-homebrew: dist-version
	brew bump-formula-pr --version $(VERSION) --no-audit --no-browse vespa-cli

dist-homebrew:
	go run cond_make.go --dist-homebrew

# Create a GitHub release draft for all platforms. Note that this only creates a
# draft, which is not publicly visible until it's explicitly published.
#
# Once the release has been created this prints an URL to the release draft.
#
# This requires the GitHub CLI to be installed: brew install gh
#
# Example:
#
# $ make dist-github
--dist-github: dist
	gh release create v$(VERSION) --repo vespa-engine/vespa --notes-file $(CURDIR)/README.md --title "Vespa CLI $(VERSION)" \
		$(DIST)/vespa-cli_$(VERSION)_sha256sums.txt \
		$(DIST)/vespa-cli_$(VERSION)_*.zip \
		$(DIST)/vespa-cli_$(VERSION)_*.tar.gz

dist-github:
	go run cond_make.go --dist-github

#
# Cross-platform build targets
#

dist: $(DIST_TARGETS) dist-sha256sums

dist-mac: GOOS=darwin
dist-mac: GOARCH=amd64

dist-mac-arm64: GOOS=darwin
dist-mac-arm64: GOARCH=arm64

dist-linux: GOOS=linux
dist-linux: GOARCH=amd64

dist-linux-arm64: GOOS=linux
dist-linux-arm64: GOARCH=arm64

dist-win32: GOOS=windows
dist-win32: GOARCH=386

dist-win64: GOOS=windows
dist-win64: GOARCH=amd64

$(DIST_TARGETS): DIST_NAME=vespa-cli_$(VERSION)_$(GOOS)_$(GOARCH)
$(DIST_TARGETS): dist-version ci manpages
$(DIST_TARGETS):
	mkdir -p $(DIST)/$(DIST_NAME)/bin
	env CGO_ENABLED=0 GOOS=$(GOOS) GOARCH=$(GOARCH) go build -o $(DIST)/$(DIST_NAME)/bin $(GO_FLAGS) ./...
	cp -a $(GIT_ROOT)/LICENSE $(DIST)/$(DIST_NAME)
	if [ "$(GOOS)" = "windows" ]; then \
		cd $(DIST) && zip -r $(DIST)/$(DIST_NAME).zip $(DIST_NAME); \
	else \
		cp -a share $(DIST)/$(DIST_NAME); \
		tar -czvf $(DIST)/$(DIST_NAME).tar.gz -C $(DIST) $(DIST_NAME); \
	fi

dist-sha256sums:
	sha256sum $(DIST)/vespa-cli_$(VERSION)_*.zip $(DIST)/vespa-cli_$(VERSION)_*.tar.gz | sed 's|$(DIST)/||' > $(DIST)/vespa-cli_$(VERSION)_sha256sums.txt

dist-version:
ifeq ($(VERSION),$(DEVEL_VERSION))
	$(error Invalid release version: $(VERSION). Try 'git checkout vX.Y.Z' or 'env VERSION=X.Y.Z make ...')
endif

install-all: all manpages

#
# Development targets
#

ci:
ifdef CI
# Ensure that CI systems use a proxy for downloading dependencies
	go env -w GOPROXY="https://proxy.golang.org,direct"
	go env
endif

install-brew:
	brew install vespa-cli

install: ci
	env GOBIN=$(BIN) go install $(GO_FLAGS) ./...

manpages: install
	mkdir -p $(SHARE)/man/man1
	$(BIN)/vespa man $(SHARE)/man/man1

clean:
	rm -rf $(DIST)
	rm -f $(BIN)/vespa $(BIN)/vespa-wrapper $(SHARE)/man/man1/vespa.1 $(SHARE)/man/man1/vespa-*.1
	rmdir -p $(BIN) $(SHARE)/man/man1 > /dev/null 2>&1 || true

test: ci
	mkdir -p $(CURDIR)/tmp && TMPDIR=$(CURDIR)/tmp go test ./...

checkfmt:
	@sh -c "test -z $$(gofmt -l .)" || { echo "one or more files need to be formatted: try make fmt to fix this automatically"; exit 1; }

fmt:
	gofmt -w .
