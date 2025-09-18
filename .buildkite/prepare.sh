#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

echo "--- 🛠️ Preparing build environment"
echo "Updating Vespa version in POMs to $VESPA_VERSION..."
"$SOURCE_DIR/.buildkite/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

echo "Creating artifact directories..."
mkdir -p "$WORKDIR/artifacts/$ARCH/rpms"
mkdir -p "$WORKDIR/artifacts/$ARCH/maven-repo"

# Assume that the latest python3 version installed and pip is installed.
# Done already in vespaengine/docker-image-build-alma* images.
