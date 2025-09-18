#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

echo "--- ⚙️ Building C++ components"
# shellcheck disable=1091
source /etc/profile.d/enable-gcc-toolset.sh

PATH=/opt/vespa-deps/bin:$PATH

cd "$SOURCE_DIR"
echo "Running make with $NUM_CPP_THREADS threads..."
make -j "$NUM_CPP_THREADS"
