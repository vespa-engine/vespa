#!/usr/bin/ssh-agent /bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1
readonly VESPA_MAJOR=$(echo $VESPA_VERSION | cut -d. -f1)

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

ssh-add -D
ssh-add <(echo $DOCKER_IMAGE_DEPLOY_KEY | base64 -d)
git clone git@github.com:vespa-engine/docker-image
cd docker-image

RELEASE_TAG="v$VESPA_VERSION"
if git rev-parse $RELEASE_TAG &> /dev/null; then
    git checkout $RELEASE_TAG
else
    git tag -a "$RELEASE_TAG" -m "Release version $VESPA_VERSION"
    git push origin "$RELEASE_TAG"
fi

#The minimal image seem to have issues building on cd.screwdriver.cd. Needs investigation.
#for data in "Dockerfile vespa" "Dockerfile.minimal vespa-minimal"; do

for data in "Dockerfile vespa"; do
    set -- $data
    DOCKER_FILE=$1
    IMAGE_NAME=$2

    # Push to Docker Hub
    if curl -fsSL https://index.docker.io/v1/repositories/vespaengine/$IMAGE_NAME/tags/$VESPA_VERSION &> /dev/null; then
        echo "Container image docker.io/vespaengine/$IMAGE_NAME:$VESPA_VERSION aldready exists."
    else
        # Build
        buildah bud \
                --build-arg VESPA_BASE_IMAGE=el8 \
                --build-arg VESPA_VERSION=$VESPA_VERSION \
                --file $DOCKER_FILE \
                --jobs 2  \
                --layers=false \
                --manifest "vespaengine/$IMAGE_NAME:$VESPA_VERSION" \
                --platform linux/amd64,linux/arm64 \
                --squash | cat

        # Test
        buildah tag vespaengine/$IMAGE_NAME:$VESPA_VERSION vespaengine/$IMAGE_NAME:latest
        $SD_SOURCE_DIR/screwdriver/test-quick-start-guide.sh

        # Publish
        buildah login --username aressem --password "$DOCKER_HUB_DEPLOY_KEY" docker.io
        buildah manifest push --all --format v2s2 vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://docker.io/vespaengine/$IMAGE_NAME:$VESPA_VERSION | cat
        buildah manifest push --all --format v2s2 vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://docker.io/vespaengine/$IMAGE_NAME:$VESPA_MAJOR | cat
        buildah manifest push --all --format v2s2 vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://docker.io/vespaengine/$IMAGE_NAME:latest | cat
    fi
done

# Push to GitHub Container Registry
JWT=$(curl -sSL -u aressem:$GHCR_DEPLOY_KEY "https://ghcr.io/token?service=ghcr.io&scope=repository:vespa-engine/vespa:pull" | jq -re '.token')
IMAGE_TAGS=$(curl -sSL -H "Authorization: Bearer $JWT" https://ghcr.io/v2/vespa-engine/vespa/tags/list | jq -re '.tags[]')
if grep $VESPA_VERSION <<< "$IMAGE_TAGS" &> /dev/null; then
    echo "Container image ghcr.io/vespa-engine/vespa:$VESPA_VERSION aldready exists."
else
    buildah login --username aressem --password "$GHCR_DEPLOY_KEY" ghcr.io
    skopeo --insecure-policy copy --retry-times 5 --all --format v2s2 docker://docker.io/vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://ghcr.io/vespa-engine/vespa:$VESPA_VERSION
    skopeo --insecure-policy copy --retry-times 5 --all --format v2s2 docker://docker.io/vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://ghcr.io/vespa-engine/vespa:$VESPA_MAJOR
    skopeo --insecure-policy copy --retry-times 5 --all --format v2s2 docker://docker.io/vespaengine/$IMAGE_NAME:$VESPA_VERSION docker://ghcr.io/vespa-engine/vespa:latest
fi
