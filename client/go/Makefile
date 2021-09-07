# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

BIN ?= $(CURDIR)/bin
SHARE ?= $(CURDIR)/share
# When building a new release the build system should set the VERSION
# environment variable to version being built
VERSION ?= $(shell echo "0.0.0-`git rev-parse --short HEAD`")

all: test checkfmt install

install:
	env GOBIN=$(BIN) \
		go install -ldflags "-X github.com/vespa-engine/vespa/client/go/build.Version=$(VERSION)" ./...

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
