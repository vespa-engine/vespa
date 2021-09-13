# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# When building a new release the build system should set the VERSION
# environment variable to version being built or released
VERSION ?= $(shell echo "0.0.0-`git rev-parse --short HEAD`")

BIN ?= $(CURDIR)/bin
SHARE ?= $(CURDIR)/share
GO_FLAGS := -ldflags "-X github.com/vespa-engine/vespa/client/go/build.Version=$(VERSION)"

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

build-mac: GOOS=darwin
build-mac: xbuild

build-linux: GOOS=linux
build-linux: xbuild

build-win: GOOS=windows
build-win: xbuild

xbuild:
	mkdir -p bin/$(GOOS)_amd64
	env GOOS=$(GOOS) go build -o bin/$(GOOS)_amd64 $(GO_FLAGS) ./...

#
# Development targets
#

install:
	env GOBIN=$(BIN) go install $(GO_FLAGS) ./...

manpages: install
	mkdir -p $(SHARE)/man/man1
	$(BIN)/vespa man $(SHARE)/man/man1

clean:
	rm -rf $(BIN) $(SHARE)

test:
	go test ./...

checkfmt:
	@bash -c "diff --line-format='%L' <(echo -n) <(gofmt -l .)" || { echo "one or more files need to be formatted: try make fmt to fix this automatically"; exit 1; }

fmt:
	gofmt -w .
