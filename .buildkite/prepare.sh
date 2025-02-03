#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

"$SOURCE_DIR/.buildkite/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

# We disable javadoc for all modules not marked as public API
for MODULE in $(comm -2 -3 \
                <(find . -name "*.java" | awk -F/ '{print $2}' | sort -u)
                <(find . -name "package-info.java" -exec grep -HnE "@(com.yahoo.api.annotations.)?PublicApi.*" {} \; | awk -F/ '{print $2}' | sort -u)); do
    mkdir -p "$MODULE/src/main/javadoc"
    echo "No javadoc available for module" > "$MODULE/src/main/javadoc/README"
done

mkdir -p "$WORKDIR/artifacts/$ARCH/rpms"
mkdir -p "$WORKDIR/artifacts/$ARCH/maven-repo"

# TODO(aressem): This should move into the vespaengine/docker-image-build-alma* image
# Make sure we use the latest python3 version installed and ensure that pip is installed
# shellcheck shell=bash disable=SC2010
PYBIN="$(ls /usr/bin/python3* | grep -E "/usr/bin/python3.[0-9]+$" |sort -n -k2 -t.|tail -1)"
alternatives --set python3 "$PYBIN"
dnf install -y "$(basename "$PYBIN")"-pip
