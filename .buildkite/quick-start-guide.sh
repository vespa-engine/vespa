#!/bin/bash

set -euo pipefail

if [[ $VESPA_USE_SANITIZER != null ]]; then
    echo "Skipping quick start guide test for sanitizer builds."
    exit 0
fi

"$SOURCE_DIR/screwdriver/test-quick-start-guide.sh"
