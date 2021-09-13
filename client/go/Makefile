# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# When building a new release the build system should set the VERSION
# environment variable to version being built or released
VERSION ?= $(shell echo "0.0.0-`git rev-parse --short HEAD`")

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

# Bump the version of the vespa-cli formula and create a pull request to Homebrew repository
dist-homebrew:
	brew bump-formula-pr --tag vespa-$(VERSION)-1 --version $(VERSION) vespa-cli

#
# Cross-platform build targets
#

dist: $(DIST_TARGETS) sha256sums

dist-mac: GOOS=darwin
dist-mac: GOARCH=amd64

dist-linux: GOOS=linux
dist-linux: GOARCH=amd64

dist-win32: GOOS=windows
dist-win32: GOARCH=386

dist-win64: GOOS=windows
dist-win64: GOARCH=amd64

$(DIST_TARGETS): DIST_NAME=vespa-cli_$(VERSION)_$(GOOS)_$(GOARCH)
$(DIST_TARGETS): manpages
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
