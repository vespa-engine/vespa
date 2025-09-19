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

echo "--- 🐹 Building Go components"
cd "$SOURCE_DIR"

echo "Installing Go client and tools..."
make -C client/go BIN="$WORKDIR/vespa-install/opt/vespa/bin" SHARE="$WORKDIR/vespa-install/usr/share" install-all
