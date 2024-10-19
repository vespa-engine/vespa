#!/bin/bash

set -euo pipefail

cd "$SOURCE_DIR"
./bootstrap.sh full

mkdir -p "$VESPA_CPP_TEST_JARS"

# shellcheck disable=2038
find . -type d -name target -exec find {} -mindepth 1 -maxdepth 1 -name "*.jar" \; | xargs -I '{}' cp '{}' "$VESPA_CPP_TEST_JARS"
