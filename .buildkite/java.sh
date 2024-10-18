#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

source /etc/profile.d/enable-gcc-toolset.sh

cd "$SOURCE_DIR"

read -ra MVN_EXTRA_OPTS <<< "$VESPA_MAVEN_EXTRA_OPTS"
./mvnw -T "$NUM_MVN_THREADS" "${MVN_EXTRA_OPTS[@]}" "$VESPA_MAVEN_TARGET"
