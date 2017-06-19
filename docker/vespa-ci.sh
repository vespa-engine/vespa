#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
set -x

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  exit 1
fi

DIR=$(dirname $(readlink -f $0))
cd $DIR

GIT_COMMIT=$1
BUILD_DOCKER_IMAGE="vespabuild"
CI_DOCKER_IMAGE="vespaci"

docker build -t "$BUILD_DOCKER_IMAGE" -f Dockerfile.build .

# Create a temporary copy of the rpm spec file inside docker directory so it can be referenced by the Dockerfile
rm -rf tmp; mkdir tmp
cp -p ../dist/vespa.spec tmp/vespa.spec

docker build -t "$CI_DOCKER_IMAGE" -f Dockerfile.ci .
docker run --rm -v $(pwd)/..:/vespa --entrypoint /vespa-ci-internal.sh "$CI_DOCKER_IMAGE" "$GIT_COMMIT" \
   2>&1 | tee vespa-ci-$(date +%Y-%m-%dT%H:%M:%S%z).log
