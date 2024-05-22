#!/bin/bash

set -euo pipefail

"$SOURCE_DIR/screwdriver/replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$SOURCE_DIR"

mkdir -p "$WORKDIR/artifacts/$ARCH/rpms"
mkdir -p "$WORKDIR/artifacts/$ARCH/maven-repo"
