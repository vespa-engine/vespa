# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

all: test checkfmt install

# When building a new release the build system should set the VERSION
# environment variable to version being built
VERSION ?= $(shell echo "0.0.0-`git rev-parse --short HEAD`")

install:
	env GOBIN=$(CURDIR)/bin \
		go install -ldflags "-X github.com/vespa-engine/vespa/client/go/build.Version=$(VERSION)" ./...

test:
	go test ./...

checkfmt:
	@bash -c "diff --line-format='%L' <(echo -n) <(gofmt -l .)" || { echo "one or more files need to be formatted: try make fmt to fix this automatically"; exit 1; }

fmt:
	gofmt -w .
