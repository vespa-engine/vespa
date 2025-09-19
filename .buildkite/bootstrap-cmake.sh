#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Generates a container tag name based on the provided arguments.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

echo "--- 🔧 Configuring CMake build"
# shellcheck disable=1091
source /etc/profile.d/enable-gcc-toolset.sh

PATH=/opt/vespa-deps/bin:$PATH

VESPA_CMAKE_SANITIZERS_OPTION=""
VESPA_CMAKE_CCACHE_OPTION=""
if [[ $VESPA_USE_SANITIZER != null ]]; then
    echo "Enabling sanitizer: $VESPA_USE_SANITIZER"
    VESPA_CMAKE_SANITIZERS_OPTION="-DVESPA_USE_SANITIZER=$VESPA_USE_SANITIZER"
    VESPA_CMAKE_CCACHE_OPTION="-DVESPA_USE_CCACHE=false"
    VALGRIND_UNIT_TESTS=false
fi
if [[ $BUILDKITE_PULL_REQUEST != "false" ]]; then
    VALGRIND_UNIT_TESTS=false
fi

echo "Running CMake configuration..."
cmake -DVESPA_UNPRIVILEGED=no -DVALGRIND_UNIT_TESTS="$VALGRIND_UNIT_TESTS" \
  "$VESPA_CMAKE_SANITIZERS_OPTION" "$VESPA_CMAKE_CCACHE_OPTION" "$SOURCE_DIR"
