#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Runs the quick start guide test.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

if [[ $VESPA_USE_SANITIZER != null ]]; then
    echo "Skipping quick start guide test for sanitizer builds."
    exit 0
fi

echo "--- 📖 Running quick start guide test"
"$SOURCE_DIR/.buildkite/test-quick-start-guide.sh"
