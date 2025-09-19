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

echo "--- ⚙️ Building C++ components"
# shellcheck disable=1091
source /etc/profile.d/enable-gcc-toolset.sh

PATH=/opt/vespa-deps/bin:$PATH

cd "$SOURCE_DIR"
echo "Running make with $NUM_CPP_THREADS threads..."
make -j "$NUM_CPP_THREADS"
