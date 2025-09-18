#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
set -o errexit
set -o nounset
set -o pipefail
set -o xtrace

if ! docker ps &> /dev/null; then
    echo "No working docker command found."
    exit 1
fi

echo "Detected running AlmaLinux: $ALMALINUX_MAJOR"

if [[ ! -d "${WORKDIR}/docker-image" ]]; then
    git clone --depth 1 https://github.com/vespa-engine/docker-image "$WORKDIR/docker-image"
fi

rm -rf "${WORKDIR}/docker-image/rpms"
cp -a "${WORKDIR}/artifacts/$ARCH/rpms" "${WORKDIR}/docker-image/"

cd "${WORKDIR}/docker-image"
SOURCE_GITREF=$(git rev-parse HEAD)

GHCR_PREVIEW_TAG="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-preview-${ARCH}" "$VESPA_VERSION")"
VESPA_BASE_IMAGE="el${ALMALINUX_MAJOR}"
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

GHCR_SYSTEMTEST_TAG="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-systemtest-preview-$ARCH" "$VESPA_VERSION")"
SYSTEM_TEST_BASE_IMAGE="almalinux:${ALMALINUX_MAJOR}"
docker build --progress=plain \
             --build-arg BASE_IMAGE="$SYSTEM_TEST_BASE_IMAGE" \
             --build-arg VESPA_BASE_IMAGE="${GHCR_PREVIEW_TAG}" \
             --target systemtest \
             --tag "$GHCR_SYSTEMTEST_TAG" \
             --file Dockerfile .
