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
docker build --progress plain \
             --build-arg SOURCE_GITREF="$SOURCE_GITREF" \
             --build-arg VESPA_VERSION="$VESPA_VERSION" \
             --tag vespaengine/vespa \
             --tag "ghcr.io/vespa-engine/vespa-preview-$ARCH:$VESPA_VERSION" \
             --file Dockerfile .

declare -r GITREF="${GITREF_SYSTEM_TEST:-HEAD}"

cd "$WORKDIR"
if [[ ! -d $WORKDIR/system-test ]]; then
    git clone --filter="blob:none" https://github.com/vespa-engine/system-test
fi

cd system-test
git checkout "$GITREF"
mkdir -p docker/vespa-systemtests
git archive HEAD --format tar | tar x -C docker/vespa-systemtests
cd docker
cp -a "$LOCAL_MVN_REPO" maven-repo
docker build --progress=plain \
             --build-arg VESPA_BASE_IMAGE= "ghcr.io/vespa-engine/vespa-preview-$ARCH:$VESPA_VERSION" \
             --target systemtest \
             --tag "docker.io/vespaengine/vespa-systemtest-preview-$ARCH:$VESPA_VERSION" \
             --file Dockerfile .

