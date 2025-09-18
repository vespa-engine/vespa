#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#

set -o errexit
set -o nounset
set -o pipefail

if [[ "${DEBUG:-}" == "true" ]]; then
    set -o xtrace
fi

if [[ $BUILDKITE != true ]]; then
    echo "Skipping container publishing when not executed by Buildkite."
    exit 0
fi

echo "--- Authenticating to GitHub Container Registry"
OPT_STATE="$(set +o)"
set +x
echo "$VESPA_ENGINE_GHCR_IO_WRITE_TOKEN" |  docker login ghcr.io --username esolitos --password-stdin
eval "$OPT_STATE"

echo "--- Publishing Vespa preview container"
VESPA_PREVIEW_CONTAINER_URI="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-preview-${ARCH}" "$VESPA_VERSION")"
echo "Pushing container: ${VESPA_PREVIEW_CONTAINER_URI}"
docker push "$VESPA_PREVIEW_CONTAINER_URI"

echo "Signing container image..."
IMAGE_SHA256=$(crane digest "$VESPA_PREVIEW_CONTAINER_URI")
cosign sign -y --oidc-provider=buildkite-agent "${VESPA_PREVIEW_CONTAINER_URI}@${IMAGE_SHA256}"

echo "Setting Buildkite metadata for Vespa container..."
buildkite-agent meta-data set "vespa-container-image-${ARCH}-al${ALMALINUX_MAJOR}" "${VESPA_PREVIEW_CONTAINER_URI}@${IMAGE_SHA256}"

echo "--- Publishing system-test container"
# Publish the system test container image
SYSTEMTEST_PREVIEW_CONTAINER_URI=$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "docker.io" "vespaengine/vespa-systemtest-preview-$ARCH" "$VESPA_VERSION")
echo "Pushing container: ${SYSTEMTEST_PREVIEW_CONTAINER_URI}"
docker push "$SYSTEMTEST_PREVIEW_CONTAINER_URI"
IMAGE_SHA256=$(crane digest "$SYSTEMTEST_PREVIEW_CONTAINER_URI")

echo "Setting Buildkite metadata for system-test container..."
buildkite-agent meta-data set "vespa-systemtest-container-image-$ARCH-al${ALMALINUX_MAJOR}" "$SYSTEMTEST_PREVIEW_CONTAINER_URI@$IMAGE_SHA256"
