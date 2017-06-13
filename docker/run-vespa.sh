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
DOCKER_IMAGE=vesparun

docker build -t "$DOCKER_IMAGE" -f Dockerfile.run .

if [ "$(uname)" != "Darwin" ]; then
  docker run -d -v $(pwd)/..:/vespa --net=host --privileged  --entrypoint /vespa/docker/run-vespa-internal.sh "$DOCKER_IMAGE" "$VESPA_VERSION"
else
  # On OS X, net=host does not work. Need to explicitly expose ports from localhost into container.
  docker run -d -p 8080:8080 -p 19071:19071 -v $(pwd)/..:/vespa --privileged  --entrypoint /vespa/docker/run-vespa-internal.sh "$DOCKER_IMAGE" "$VESPA_VERSION"
fi
