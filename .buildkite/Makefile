# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
export TOP := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))

export ARCH ?= $(shell if [[ $$(arch) == x86_64 ]]; then echo amd64; else echo arm64; fi)
export VESPA_BUILDOS_LABEL ?= alma8
export VESPA_MAJOR_VERSION_LABEL ?= vespa8

export LOG_DIR ?= $(shell pwd)
export SOURCE_DIR ?= $(shell pwd)
export WORKDIR ?= $(shell pwd)
export VESPA_CPP_TEST_JARS :=$(WORKDIR)/VESPA_CPP_TEST_JARS

export VESPA_VERSION ?= 8.999.1
export FACTORY_VESPA_VERSION := $(VESPA_VERSION)
export VALGRIND_UNIT_TESTS ?= false
export VESPA_USE_SANITIZER ?= null

export LOCAL_MVN_REPO ?= $(WORKDIR)/artifacts/$(ARCH)/maven-repo
export VESPA_MAVEN_EXTRA_OPTS ?= --show-version --batch-mode --no-snapshot-updates  \
	-DaltDeploymentRepository=local-repo::default::file:$(LOCAL_MVN_REPO)

export LOCAL_RPM_REPO ?= $(WORKDIR)/artifacts/$(ARCH)/rpms

export NUM_CPU_LIMIT ?= $(shell nproc)
export NUM_CPP_THREADS := $(shell echo $$(( $(NUM_CPU_LIMIT)*2/3 )))
export NUM_MVN_THREADS := $(shell echo $$(( $(NUM_CPU_LIMIT)*2/3 )))

export MAVEN_OPTS ?= -Xms1g -Xmx$(NUM_MVN_THREADS)g

export BUILDKITE ?= false
export BUILDKITE_PULL_REQUEST ?= true
ifeq ($(BUILDKITE_PULL_REQUEST),false)
	export VESPA_MAVEN_TARGET ?= deploy
else
	export VESPA_MAVEN_TARGET ?= install
	export VESPA_MAVEN_EXTRA_OPTS := $(VESPA_MAVEN_EXTRA_OPTS) -Dmaven.source.skip=true -Dmaven.javadoc.skip=true
endif

# This must be set by Buildkite pipelines; we provide a default for testing only.
export VESPA_CONTAINER_IMAGE_VERSION_TAG_SUFFIX ?= -dev-only

.DEFAULT_GOAL := pr

main: build-rpms cpp-test quick-start-guide publish-container publish-artifacts
pr: build-rpms cpp-test basic-search-test

check:
	@shellcheck -x $(TOP)/*.sh

prepare: check
	@$(TOP)/execute.sh $@

bootstrap: prepare
	@$(TOP)/execute.sh $@

bootstrap-cmake: bootstrap
	@$(TOP)/execute.sh $@

java: bootstrap
	@$(TOP)/execute.sh $@

cpp: bootstrap-cmake
	@$(TOP)/execute.sh $@

cpp-test: cpp
	@$(TOP)/execute.sh $@

install: cpp java cpp-test
	@$(TOP)/execute.sh $@

build-rpms: install
	@$(TOP)/execute.sh $@

basic-search-test: build-rpms
	@$(TOP)/execute.sh $@

build-container: build-rpms
	@$(TOP)/execute.sh $@

quick-start-guide: build-container
	@$(TOP)/execute.sh $@

publish-container: build-container
	@$(TOP)/execute.sh $@

publish-artifacts: java build-rpms
	@$(TOP)/execute.sh $@

upload-test-results: java cpp-test
	@$(TOP)/execute.sh $@

.PHONY: \
	main \
	pr \
	prepare \
	bootstrap \
	bootstrap-cmake \
	java \
	cpp \
	cpp-test \
	install \
	build-rpms \
	basic-search-test \
	build-container \
	quick-start-guide \
	publish-container \
	publish-artifacts \
	upload-test-results \
	check
