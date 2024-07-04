#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1
readonly VESPA_MAJOR=$(echo $VESPA_VERSION | cut -d. -f1)

if [[ -z "$DOCKER_HUB_DEPLOY_TOKEN" ]]; then
    echo "Environment variable DOCKER_HUB_DEPLOY_TOKEN must be set, but is empty."
    exit 1
fi
if [[ -z "$GHCR_DEPLOY_TOKEN" ]]; then
    echo "Environment variable GHCR_DEPLOY_TOKEN must be set, but is empty."
    exit 1
fi

crane auth login -u aressem -p $GHCR_DEPLOY_TOKEN ghcr.io
SRC_IMAGE=ghcr.io/vespa-engine/vespa-preview:$VESPA_VERSION
SRC_IMAGE_DIGEST=$(crane digest $SRC_IMAGE)
cosign verify \
    --certificate-identity https://buildkite.com/vespaai/vespa-engine-vespa \
    --certificate-oidc-issuer https://agent.buildkite.com \
    $SRC_IMAGE@$SRC_IMAGE_DIGEST

# Copy to Docker Hub
if curl -fsSL https://hub.docker.com/v2/repositories/vespaengine/vespa/tags/$VESPA_VERSION &> /dev/null; then
    echo "Container image docker.io/vespaengine/vespa:$VESPA_VERSION already exists."
else
  DST_IMAGE=docker.io/vespaengine/vespa:$VESPA_VERSION
  crane auth login -u aressem -p $DOCKER_HUB_DEPLOY_TOKEN docker.io
  crane cp $SRC_IMAGE@$SRC_IMAGE_DIGEST $DST_IMAGE
  crane tag $DST_IMAGE $VESPA_MAJOR
  crane tag $DST_IMAGE latest
fi

# Copy to GitHub Container Registry
JWT=$(curl -sSL -u aressem:$GHCR_DEPLOY_TOKEN "https://ghcr.io/token?service=ghcr.io&scope=repository:vespa-engine/vespa:pull" | jq -re '.token')
IMAGE_TAGS=$(curl -sSL -H "Authorization: Bearer $JWT" https://ghcr.io/v2/vespa-engine/vespa/tags/list | jq -re '.tags[]')
if grep $VESPA_VERSION <<< "$IMAGE_TAGS" &> /dev/null; then
    echo "Container image ghcr.io/vespa-engine/vespa:$VESPA_VERSION already exists."
else
  DST_IMAGE=ghcr.io/vespa-engine/vespa:$VESPA_VERSION
  crane cp $SRC_IMAGE@$SRC_IMAGE_DIGEST $DST_IMAGE
  crane tag $DST_IMAGE $VESPA_MAJOR
  crane tag $DST_IMAGE latest
fi
