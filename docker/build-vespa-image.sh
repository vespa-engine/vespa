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

docker build -t "$DOCKER_IMAGE":"$VESPA_VERSION" --build-arg VESPA_VERSION="$VESPA_VERSION" -f Dockerfile.run .
