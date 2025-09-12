#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -o errexit
set -o nounset
set -o pipefail

if [[ "${DEBUG:-}" == "true" ]]; then
    set -o xtrace
fi

if ! docker ps &> /dev/null; then
    echo "No working docker command found."
    exit 1
fi

# Detect AlmaLinux major version from current OS
if [[ -f /etc/almalinux-release ]]; then
  AL_MAJOR_VERSION=$(grep -oP 'release \K[0-9]+' /etc/almalinux-release | head -1)
else
  echo "Warning: Cannot detect AlmaLinux version, defaulting to 8"
  AL_MAJOR_VERSION=8
fi
echo "Detected running AlmaLinux: $AL_MAJOR_VERSION"
VESPA_BASE_IMAGE="el${AL_MAJOR_VERSION}"

if [[ ! -d "${WORKDIR}/docker-image" ]]; then
    git clone --depth 1 https://github.com/vespa-engine/docker-image "$WORKDIR/docker-image"
fi

rm -rf "${WORKDIR}/docker-image/rpms"
cp -a "${WORKDIR}/artifacts/$ARCH/rpms" "${WORKDIR}/docker-image/"

cd "${WORKDIR}/docker-image"
SOURCE_GITREF=$(git rev-parse HEAD)

# Set default tag to be pointing to Almalinux 8 version.
# TODO: Update when we switch default OS
GHCR_PREVIEW_TAG="ghcr.io/vespa-engine/vespa-preview-$ARCH:${VESPA_VERSION}"
if [[ "$AL_MAJOR_VERSION" != "8" ]]; then
    GHCR_PREVIEW_TAG+="-al${AL_MAJOR_VERSION}"
fi

docker build --progress plain \
             --build-arg SOURCE_GITREF="$SOURCE_GITREF" \
             --build-arg VESPA_VERSION="$VESPA_VERSION" \
             --build-arg VESPA_BASE_IMAGE="$VESPA_BASE_IMAGE" \
             --tag vespaengine/vespa \
             --tag "${GHCR_PREVIEW_TAG}" \
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
rm -rf maven-repo
cp -a "$HOME/.m2/repository" maven-repo
rm -rf rpms
mv "$WORKDIR/docker-image/rpms" rpms

# Set default systemtest image to use AlmaLinux 8 version
# TODO: Update when we switch default OS
GHCR_SYSTEMTEST_TAG="ghcr.io/vespa-engine/vespa-systemtest-preview-$ARCH:$VESPA_VERSION"
if [[ "$AL_MAJOR_VERSION" != "8" ]]; then
    GHCR_SYSTEMTEST_TAG+="-al${AL_MAJOR_VERSION}"
fi

SYSTEM_TEST_BASE_IMAGE="almalinux:${AL_MAJOR_VERSION}"
docker build --progress=plain \
             --build-arg BASE_IMAGE="$SYSTEM_TEST_BASE_IMAGE" \
             --build-arg VESPA_BASE_IMAGE="${GHCR_PREVIEW_TAG}" \
             --target systemtest \
             --tag "$GHCR_SYSTEMTEST_TAG" \
             --file Dockerfile .
