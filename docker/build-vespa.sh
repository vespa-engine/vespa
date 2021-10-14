#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
  exit 1
fi

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

VESPA_VERSION=$1
DOCKER_IMAGE="centos:7"

docker pull ${DOCKER_IMAGE}

docker run --rm -v $(pwd)/..:/vespa -w /vespa --entrypoint /vespa/docker/build/build-vespa-internal.sh "$DOCKER_IMAGE" "$VESPA_VERSION" "$(id -u)" "$(id -g)"
