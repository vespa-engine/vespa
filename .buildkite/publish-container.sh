#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $BUILDKITE != true ]]; then
    echo "Skipping container publishing when not executed by Buildkite."
    exit 0
fi

OPT_STATE="$(set +o)"
set +x
echo "$VESPA_ENGINE_GHCR_IO_WRITE_TOKEN" |  docker login ghcr.io --username esolitos --password-stdin
eval "$OPT_STATE"
VESPA_PREVIEW_CONTAINER_URI="$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-preview-${ARCH}" "$VESPA_VERSION")"
docker push "$VESPA_PREVIEW_CONTAINER_URI"

IMAGE_SHA256=$(crane digest "$VESPA_PREVIEW_CONTAINER_URI")
cosign sign -y --oidc-provider=buildkite-agent "${VESPA_PREVIEW_CONTAINER_URI}@${IMAGE_SHA256}"

buildkite-agent meta-data set "vespa-container-image-${ARCH}-al${ALMALINUX_MAJOR}" "${VESPA_PREVIEW_CONTAINER_URI}@${IMAGE_SHA256}"

# Publish the system test container image
SYSTEMTEST_PREVIEW_CONTAINER_URI=$("${WORKDIR}/.buildkite/utils/get-container-tag.sh" "ghcr.io" "vespa-engine/vespa-systemtest-preview-$ARCH" "$VESPA_VERSION")
docker push "$SYSTEMTEST_PREVIEW_CONTAINER_URI"
IMAGE_SHA256=$(crane digest "$SYSTEMTEST_PREVIEW_CONTAINER_URI")

buildkite-agent meta-data set "vespa-systemtest-container-image-$ARCH-al${ALMALINUX_MAJOR}" "$SYSTEMTEST_PREVIEW_CONTAINER_URI@$IMAGE_SHA256"
