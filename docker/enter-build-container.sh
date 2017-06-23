#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 0 ]; then
  echo "Usage: $0"
  exit 1
fi

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

DOCKER_IMAGE="vespabuild"

docker build -t "$DOCKER_IMAGE" -f build/Dockerfile .
docker run -ti --rm -v $(pwd)/..:/vespa --entrypoint /vespa/docker/enter-build-container-internal.sh "$DOCKER_IMAGE"

