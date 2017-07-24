#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
set -x

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  exit 1
fi

RELATIVE_DIR=$(dirname "${BASH_SOURCE[0]}")
DIR=$(cd "${RELATIVE_DIR}" && pwd)
cd $DIR

GIT_COMMIT=$1
DOCKER_IMAGE="vespaengine/vespa-dev:latest"

docker run --rm -v ${DIR}/..:/vespa --entrypoint /vespa/docker/ci/vespa-ci-internal.sh "$DOCKER_IMAGE" "$GIT_COMMIT" \
   2>&1 | tee vespa-ci-$(date +%Y-%m-%dT%H:%M:%S%z).log

# Needed because of piping docker run to tee above
exit ${PIPESTATUS[0]}
