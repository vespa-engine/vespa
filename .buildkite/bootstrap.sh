#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Runs Vespa bootstrap and prepares the environment for C++ tests.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

echo "--- 🔧 Running Vespa bootstrap"
cd "$SOURCE_DIR"
./bootstrap.sh full

echo "Setting up test JAR directory..."
mkdir -p "$VESPA_CPP_TEST_JARS"

echo "Collecting JAR files for C++ tests..."
# shellcheck disable=2038
find . -type d -name target -exec find {} -mindepth 1 -maxdepth 1 -name "*.jar" \; | xargs -I '{}' cp '{}' "$VESPA_CPP_TEST_JARS"
