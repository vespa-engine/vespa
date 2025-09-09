#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

"$SOURCE_DIR/.buildkite/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

cd $SOURCE_DIR
find . -name "*.java" |
    awk -F/ '{print $2}' | sort -u > /tmp/list.java-modules.txt
find . -name "package-info.java" -print0 | xargs -0 grep -HnE "@(com.yahoo.api.annotations.)?PublicApi.*"  |
    awk -F/ '{print $2}' | sort -u > /tmp/list.public-api-modules.txt

# We disable javadoc for all modules not marked as public API
for MODULE in $(comm -2 -3 /tmp/list.java-modules.txt /tmp/list.public-api-modules.txt); do
    mkdir -p "$MODULE/src/main/javadoc"
    echo "No javadoc available for module" > "$MODULE/src/main/javadoc/README"
done

mkdir -p "$WORKDIR/artifacts/$ARCH/rpms"
mkdir -p "$WORKDIR/artifacts/$ARCH/maven-repo"

# Assume that the latest python3 version installed and pip is installed.
# Done already in vespaengine/docker-image-build-alma* images.
