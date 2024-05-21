#!/bin/bash

set -euo pipefail

if ! docker ps &> /dev/null; then
    echo "No working docker command found."
    exit 1
fi

if [[ ! -d $WORKDIR/docker-image ]]; then
    git clone --depth 1 https://github.com/vespa-engine/docker-image "$WORKDIR/docker-image"
fi

mkdir -p docker-image/rpms
cp -a "$WORKDIR/artifacts/$ARCH/rpms" docker-image/

cd "$WORKDIR/docker-image"
SOURCE_GITREF=$(git rev-parse HEAD)
docker build --progress plain --build-arg SOURCE_GITREF="$SOURCE_GITREF" --build-arg VESPA_VERSION="$VESPA_VERSION" -t vespaengine/vespa -t "ghcr.io/vespa-engine/vespa-preview-$ARCH:$VESPA_VERSION" -f Dockerfile .

