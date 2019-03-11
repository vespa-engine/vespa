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
DOCKER_IMAGE="centos:latest"

docker pull ${DOCKER_IMAGE}

# The RPMs will be put in the same directory as this script (/vespa/docker
# within the container)
docker run -w /vespa --rm -v $(pwd)/..:/vespa \
       --entrypoint /vespa/docker/build/build-vespa-internal.sh \
       "$DOCKER_IMAGE" \
       "$VESPA_VERSION" "$(id -u)" "$(id -g)" /tmp/rpmbuild /vespa/docker
