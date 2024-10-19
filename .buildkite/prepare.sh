#!/bin/bash

set -euo pipefail

"$SOURCE_DIR/screwdriver/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

# We disable javadoc for all modules not marked as public API
for MODULE in $(comm -2 -3 \
                <(find . -name "*.java" | awk -F/ '{print $2}' | sort -u)
                <(find . -name "package-info.java" -exec grep -HnE "@(com.yahoo.api.annotations.)?PublicApi.*" {} \; | awk -F/ '{print $2}' | sort -u)); do
    mkdir -p "$MODULE/src/main/javadoc"
    echo "No javadoc available for module" > "$MODULE/src/main/javadoc/README"
done

mkdir -p "$WORKDIR/artifacts/$ARCH/rpms"
mkdir -p "$WORKDIR/artifacts/$ARCH/maven-repo"
