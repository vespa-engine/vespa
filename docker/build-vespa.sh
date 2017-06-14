#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
  exit 1
fi

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

VESPA_VERSION=$1
DOCKER_IMAGE="vespabuild"

docker build -t "$DOCKER_IMAGE" -f Dockerfile.build .
docker run --rm -v $(pwd)/..:/vespa --entrypoint /vespa/docker/build-vespa-internal.sh "$DOCKER_IMAGE" "$VESPA_VERSION" "$(id -u)" "$(id -g)"

