# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# The version to release. Defaults to the current tag or revision.
# Use env VERSION=X.Y.Z make ... to override
VERSION ?= $(shell git describe --tags 2> /dev/null | sed -E "s/^vespa-|-1$$//g")
DEVEL_VERSION := $(shell echo "0.0.0-`git rev-parse --short HEAD`")
ifeq ($(VERSION),)
	VERSION = $(DEVEL_VERSION)
endif

BIN ?= $(CURDIR)/bin
SHARE ?= $(CURDIR)/share
DIST ?= $(CURDIR)/dist

GO_FLAGS := -ldflags "-X github.com/vespa-engine/vespa/client/go/build.Version=$(VERSION)"
GIT_ROOT := $(shell git rev-parse --show-toplevel)
DIST_TARGETS := dist-mac dist-linux dist-win32 dist-win64

all: test checkfmt install

#
# Dist targets
#

# Bump the version of the vespa-cli formula and create a pull request to Homebrew repository.
#
# Example:
#
# $ git checkout vespa-X.Y.Z-1
# $ make dist-github
dist-homebrew: dist-version
	brew bump-formula-pr --tag vespa-$(VERSION)-1 --version $(VERSION) vespa-cli

# Create a GitHub release draft for all platforms. Note that this only creates a
# draft, which is not publicly visible until it's explicitly published.
#
# Once the release has been created this prints an URL to the release draft.
#
# This requires the GitHub CLI to be installed: brew install gh
#
# Example:
#
# $ git checkout vespa-X.Y.Z-1
# $ make dist-github
dist-github: dist
	gh release create v$(VERSION) --repo vespa-engine/vespa --notes-file $(CURDIR)/README.md --draft --title "Vespa CLI $(VERSION)" \
		$(DIST)/vespa-cli_$(VERSION)_sha256sums.txt \
		$(DIST)/vespa-cli_$(VERSION)_*.{zip,tar.gz}

#
# Cross-platform build targets
#

dist: $(DIST_TARGETS) dist-sha256sums

dist-mac: GOOS=darwin
dist-mac: GOARCH=amd64

dist-linux: GOOS=linux
dist-linux: GOARCH=amd64

dist-win32: GOOS=windows
dist-win32: GOARCH=386

dist-win64: GOOS=windows
dist-win64: GOARCH=amd64

$(DIST_TARGETS): DIST_NAME=vespa-cli_$(VERSION)_$(GOOS)_$(GOARCH)
$(DIST_TARGETS): dist-version manpages
$(DIST_TARGETS):
	mkdir -p $(DIST)/$(DIST_NAME)/bin
	env GOOS=$(GOOS) GOARCH=$(GOARCH) go build -o $(DIST)/$(DIST_NAME)/bin $(GO_FLAGS) ./...
	cp -a $(GIT_ROOT)/LICENSE $(DIST)/$(DIST_NAME)
	if [ "$(GOOS)" = "windows" ]; then \
		cd $(DIST) && zip -r $(DIST)/$(DIST_NAME).zip $(DIST_NAME); \
	else \
		cp -a share $(DIST)/$(DIST_NAME); \
		tar -czvf $(DIST)/$(DIST_NAME).tar.gz -C $(DIST) $(DIST_NAME); \
	fi

dist-sha256sums:
	cd $(DIST) && sha256sum vespa-cli_$(VERSION)_*.{zip,tar.gz} > vespa-cli_$(VERSION)_sha256sums.txt

dist-version:
ifeq ($(VERSION),$(DEVEL_VERSION))
	$(error Invalid release version: $(VERSION). Try 'git checkout vespa-X.Y.Z-1' or 'env VERSION=X.Y.Z make ...')
endif

#
# Development targets
#

install:
	env GOBIN=$(BIN) go install $(GO_FLAGS) ./...

manpages: install
	mkdir -p $(SHARE)/man/man1
	$(BIN)/vespa man $(SHARE)/man/man1

clean:
	rm -rf $(BIN) $(SHARE) $(DIST)

test:
	go test ./...

checkfmt:
	@bash -c "diff --line-format='%L' <(echo -n) <(gofmt -l .)" || { echo "one or more files need to be formatted: try make fmt to fix this automatically"; exit 1; }

fmt:
	gofmt -w .
