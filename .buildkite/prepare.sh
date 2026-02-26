#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Prepares the build environment by updating Vespa version in POMs and creating artifact directories.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

: "${VESPA_VERSION:?Environment variable VESPA_VERSION must be set (version to build)}"
: "${SOURCE_DIR:?Environment variable SOURCE_DIR must be set (path to source code)}"
: "${LOCAL_RPM_REPO:?Environment variable LOCAL_RPM_REPO must be set (path to local RPM repo)}"
: "${LOCAL_MVN_REPO:?Environment variable LOCAL_MVN_REPO must be set (path to local Maven repo)}"

echo "--- 🛠️ Preparing build environment"
echo "Updating Vespa version in POMs to $VESPA_VERSION..."
"$SOURCE_DIR/.buildkite/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

echo "Creating artifact directories..."
mkdir -p "$LOCAL_RPM_REPO"
mkdir -p "$LOCAL_MVN_REPO"
