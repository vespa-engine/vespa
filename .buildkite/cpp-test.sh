#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

echo "--- 🧪 Running C++ tests"
PATH=/opt/vespa-deps/bin:$PATH

echo "Running ctest with $NUM_CPU_LIMIT parallel jobs..."
ctest --output-junit "$LOG_DIR/vespa-cpptest-results.xml" --output-on-failure -j "$NUM_CPU_LIMIT"
