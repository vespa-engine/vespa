#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1

if [[ -z "$DOCKER_HUB_DEPLOY_KEY" ]]; then
    echo "Environment variable DOCKER_HUB_DEPLOY_KEY must be set, but is empty."
    exit 1
fi
if [[ -z "$GHCR_DEPLOY_KEY" ]]; then
    echo "Environment variable GHCR_DEPLOY_KEY must be set, but is empty."
    exit 1
fi

BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT
cd $BUILD_DIR

git clone --depth 1 https://github.com/vespa-engine/docker-image
cd docker-image

docker info
docker version
docker buildx version
docker buildx install

unset DOCKER_HOST
docker context create vespa-context --docker "host=tcp://localhost:2376,ca=/certs/client/ca.pem,cert=/certs/client/cert.pem,key=/certs/client/key.pem"
docker context use vespa-context

docker buildx create --name vespa-builder --driver docker-container --use
docker buildx inspect --bootstrap

# Push to Docker Hub
if curl -fsSL https://index.docker.io/v1/repositories/vespaengine/vespa/tags/$VESPA_VERSION &> /dev/null; then
    echo "Container image docker.io/vespaengine/vespa:$VESPA_VERSION aldready exists."
else
    docker login --username aressem --password "$DOCKER_HUB_DEPLOY_KEY"
    docker buildx build --progress plain --push --platform linux/amd64,linux/arm64 --build-arg VESPA_VERSION=$VESPA_VERSION \
                        --tag docker.io/vespaengine/vespa:$VESPA_VERSION --tag docker.io/vespaengine/vespa:latest .
fi

# Push to GitHub Container Registry
JWT=$(curl -sSL -u aressem:$GHCR_DEPLOY_KEY "https://ghcr.io/token?service=ghcr.io&scope=repository:vespa-engine/vespa:pull" | jq -re '.token')
IMAGE_TAGS=$(curl -sSL -H "Authorization: Bearer $JWT" https://ghcr.io/v2/vespa-engine/vespa/tags/list | jq -re '.tags[]')
if grep $VESPA_VERSION <<< "$IMAGE_TAGS" &> /dev/null; then
    echo "Container image ghcr.io/vespa-engine/vespa:$VESPA_VERSION aldready exists."
else
    docker login --username aressem --password "$GHCR_DEPLOY_KEY" ghcr.io
    docker buildx build --progress plain --push --platform linux/amd64,linux/arm64 --build-arg VESPA_VERSION=$VESPA_VERSION \
                        --tag ghcr.io/vespa-engine/vespa:$VESPA_VERSION  --tag ghcr.io/vespa-engine/vespa:latest .
fi

