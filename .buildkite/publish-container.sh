#!/bin/bash

set -euo pipefail

if [[ $BUILDKITE != true ]]; then
    echo "Skipping container publishing when not executed by Buildkite."
    exit 0
fi

OPT_STATE="$(set +o)"
set +x
echo "$VESPA_ENGINE_GHCR_IO_WRITE_TOKEN" |  docker login ghcr.io --username aressem --password-stdin
eval "$OPT_STATE"
docker push "ghcr.io/vespa-engine/vespa-preview-$ARCH:$VESPA_VERSION"

IMAGE_SHA256=$(crane digest "ghcr.io/vespa-engine/vespa-preview-$ARCH:$VESPA_VERSION")

cosign sign -y --oidc-provider=buildkite-agent "ghcr.io/vespa-engine/vespa-preview-$ARCH@$IMAGE_SHA256"

buildkite-agent meta-data set "vespa-container-image-$ARCH" "ghcr.io/vespa-engine/vespa-preview-$ARCH@$IMAGE_SHA256"

# Publish the system test container image
docker push "docker.io/vespaengine/vespa-systemtest-preview-$ARCH:$VESPA_VERSION"
IMAGE_SHA256=$(crane digest "docker.io/vespaengine/vespa-systemtest-preview-$ARCH:$VESPA_VERSION")

buildkite-agent meta-data set "vespa-systemtest-container-image-$ARCH" "docker.io/vespaengine/vespa-systemtest-preview-$ARCH@$IMAGE_SHA256"

