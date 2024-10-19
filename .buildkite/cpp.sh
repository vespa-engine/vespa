#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

# shellcheck disable=1091
source /etc/profile.d/enable-gcc-toolset.sh

cd "$SOURCE_DIR"
make -j "$NUM_CPP_THREADS"
