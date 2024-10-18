#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $VESPA_USE_SANITIZER != null ]]; then
    echo "Skipping quick start guide test for sanitizer builds."
    exit 0
fi

"$SOURCE_DIR/.buildkite/test-quick-start-guide.sh"
