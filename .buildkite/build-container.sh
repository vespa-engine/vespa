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

case "${VESPA_BUILDOS_LABEL}" in
    alma8)
        VESPA_BASE_IMAGE="el8"
        SYSTEM_TEST_BASE_IMAGE="almalinux:8"
        ;;
    alma9)
        VESPA_BASE_IMAGE="el9"
        SYSTEM_TEST_BASE_IMAGE="almalinux:9"
        ;;
    *)
        echo "Unknown build os: ${VESPA_BUILDOS_LABEL}" 1>&2
        exit 1
        ;;
esac

echo "--- Setting up docker-image repository"
if [[ ! -d "${WORKDIR}/docker-image" ]]; then
    echo "Cloning docker-image repository..."
    git clone --quiet --depth 1 https://github.com/vespa-engine/docker-image "$WORKDIR/docker-image"
else
    echo "Using existing docker-image repository"
fi

echo "Preparing RPMs for container build..."
# Ensure clean state for rpms directory
rm -rf "${WORKDIR}/docker-image/rpms" && mkdir -p "${WORKDIR}/docker-image/rpms"
# Note: Appending "./" ensures that the directory's contents are copied, rather than the directory itself.
cp -a "${LOCAL_RPM_REPO}/." "${WORKDIR}/docker-image/rpms/"

cd "${WORKDIR}/docker-image"
SOURCE_GITREF=$(git rev-parse HEAD)

select_dockerfile() {
    wanted="Dockefile.${VESPA_BUILDOS_LABEL}"
    if [ -f "${wanted}" ]; then
        echo "${wanted}"
    else
        echo "Dockerfile"
    fi
}

echo "--- Building Vespa preview container"
GHCR_PREVIEW_TAG=ghcr.io/vespa-engine/vespa-preview-${ARCH}:${VESPA_VERSION}${VESPA_CONTAINER_IMAGE_VERSION_TAG_SUFFIX}
echo "Building container with tag: ${GHCR_PREVIEW_TAG}"
docker build --progress plain \
             --build-arg SOURCE_GITREF="$SOURCE_GITREF" \
             --build-arg VESPA_VERSION="$VESPA_VERSION" \
             --build-arg VESPA_BASE_IMAGE="$VESPA_BASE_IMAGE" \
             --tag vespaengine/vespa \
             --tag "${GHCR_PREVIEW_TAG}" \
             --file "$(select_dockerfile)" .

declare -r GITREF="${GITREF_SYSTEM_TEST:-HEAD}"

echo "--- Setting up system-test repository"
cd "$WORKDIR"
if [[ ! -d $WORKDIR/system-test ]]; then
    echo "Cloning system-test repository..."
    git clone --quiet --filter="blob:none" https://github.com/vespa-engine/system-test
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
DOCKER_SYSTEMTEST_TAG=docker.io/vespaengine/vespa-systemtest-preview-${ARCH}:${VESPA_VERSION}${VESPA_CONTAINER_IMAGE_VERSION_TAG_SUFFIX}
echo "Building system-test container with tag: ${DOCKER_SYSTEMTEST_TAG}"
docker build --progress=plain \
             --build-arg BASE_IMAGE="$SYSTEM_TEST_BASE_IMAGE" \
             --build-arg VESPA_BASE_IMAGE="${GHCR_PREVIEW_TAG}" \
             --target systemtest \
             --tag "$DOCKER_SYSTEMTEST_TAG" \
             --file "$(select_dockerfile)" .
