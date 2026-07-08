#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Runs C++ tests using ctest.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi
: "${NUM_CPU_LIMIT:?Environment variable NUM_CPU_LIMIT must be set (CPU limit)}"
: "${LOG_DIR:?Environment variable LOG_DIR must be set (log directory)}"
echo "--- 🧪 Running C++ tests"
PATH=/opt/vespa-deps/bin:$PATH

echo "Running ctest with $NUM_CPU_LIMIT parallel jobs..."
ctest --output-junit "$LOG_DIR/vespa-cpptest-results.xml" --output-on-failure -j "$NUM_CPU_LIMIT"
