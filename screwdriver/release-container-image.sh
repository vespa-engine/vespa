#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1

if curl -fsSL https://index.docker.io/v1/repositories/vespaengine/vespa/tags/$VESPA_VERSION &> /dev/null; then
    echo "Container image docker.io/vespaengine/vespa:$VESPA_VERSION aldready exists."
    exit 0
fi

if [[ -z "$DOCKER_HUB_DEPLOY_KEY" ]]; then
    echo "Environment variable DOCKER_HUB_DEPLOY_KEY must be set, but is empty."
    exit 1
fi

BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT
cd $BUILD_DIR

git clone --depth 1 https://github.com/vespa-engine/docker-image
cd docker-image

docker login --username aressem --password "$DOCKER_HUB_DEPLOY_KEY"
docker build --build-arg VESPA_VERSION=$VESPA_VERSION --tag docker.io/vespaengine/vespa:$VESPA_VERSION --tag docker.io/vespaengine/vespa:latest .

docker push docker.io/vespaengine/vespa:$VESPA_VERSION
docker push docker.io/vespaengine/vespa:latest
