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

GO_FLAGS := -ldflags "-X github.com/vespa-engine/vespa/client/go/build.Version=$(VERSION)"
GIT_ROOT := $(shell git rev-parse --show-toplevel)

all: checkfmt install

#
# Development targets
#

install:
	env GOBIN=$(BIN) go install $(GO_FLAGS) ./...

clean:
	rm -f $(BIN)/vespa-logfmt
	rmdir $(BIN)

checkfmt:
	@bash -c "diff --line-format='%L' <(echo -n) <(gofmt -l .)" || { echo "one or more files need to be formatted: try make fmt to fix this automatically"; exit 1; }

fmt:
	gofmt -w .
