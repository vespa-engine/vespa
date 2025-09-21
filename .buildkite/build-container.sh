#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

if ! docker ps &> /dev/null; then
    echo "No working docker command found."
    exit 1
fi

echo "Detected running AlmaLinux: $ALMALINUX_MAJOR"

echo "--- Setting up docker-image repository"
if [[ ! -d "${WORKDIR}/docker-image" ]]; then
    echo "Cloning docker-image repository..."
    git clone --depth 1 https://github.com/vespa-engine/docker-image "$WORKDIR/docker-image"
else
    echo "Using existing docker-image repository"
fi

echo "Preparing RPMs for container build..."
rm -rf "${WORKDIR}/docker-image/rpms"
cp -a "${WORKDIR}/artifacts/$ARCH/rpms" "${WORKDIR}/docker-image/"

cd "${WORKDIR}/docker-image"
SOURCE_GITREF=$(git rev-parse HEAD)

echo "--- Building Vespa preview container"
GHCR_PREVIEW_TAG="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-preview-${ARCH}" "$VESPA_VERSION")"
echo "Building container with tag: ${GHCR_PREVIEW_TAG}"
VESPA_BASE_IMAGE="el${ALMALINUX_MAJOR}"
docker build --progress plain \
             --build-arg SOURCE_GITREF="$SOURCE_GITREF" \
             --build-arg VESPA_VERSION="$VESPA_VERSION" \
             --build-arg VESPA_BASE_IMAGE="$VESPA_BASE_IMAGE" \
             --tag vespaengine/vespa \
             --tag "${GHCR_PREVIEW_TAG}" \
             --file Dockerfile .

declare -r GITREF="${GITREF_SYSTEM_TEST:-HEAD}"

echo "--- Setting up system-test repository"
cd "$WORKDIR"
if [[ ! -d $WORKDIR/system-test ]]; then
    echo "Cloning system-test repository..."
    git clone --filter="blob:none" https://github.com/vespa-engine/system-test
else
    echo "Using existing system-test repository"
fi

echo "Preparing system-test environment (checking out ${GITREF})..."
cd system-test
git checkout "$GITREF"
mkdir -p docker/vespa-systemtests
git archive HEAD --format tar | tar x -C docker/vespa-systemtests
cd docker
echo "Copying Maven repository and RPMs for system-test container..."
rm -rf maven-repo
cp -a "$HOME/.m2/repository" maven-repo
rm -rf rpms
mv "$WORKDIR/docker-image/rpms" rpms

echo "--- Building system-test container"
DOCKER_SYSTEMTEST_TAG="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "docker.io" "vespaengine/vespa-systemtest-preview-$ARCH" "$VESPA_VERSION")"
echo "Building system-test container with tag: ${DOCKER_SYSTEMTEST_TAG}"
SYSTEM_TEST_BASE_IMAGE="almalinux:${ALMALINUX_MAJOR}"
docker build --progress=plain \
             --build-arg BASE_IMAGE="$SYSTEM_TEST_BASE_IMAGE" \
             --build-arg VESPA_BASE_IMAGE="${GHCR_PREVIEW_TAG}" \
             --target systemtest \
             --tag "$DOCKER_SYSTEMTEST_TAG" \
             --file Dockerfile .
