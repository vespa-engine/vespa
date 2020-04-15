#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  exit 1
fi

RELATIVE_DIR=$(dirname "${BASH_SOURCE[0]}")
DIR=$(cd "${RELATIVE_DIR}" && pwd)
cd "${DIR}"

GIT_COMMIT=$1
DOCKER_IMAGE="vespaengine/vespa-build-centos7:latest"
INTERNAL_DIR=/vespa
LOG_PREFIX=vespa-ci-$(date +%Y-%m-%dT%H:%M:%S)

mkdir -p logs

docker run --rm -v ${DIR}/..:${INTERNAL_DIR} --entrypoint "${INTERNAL_DIR}/docker/ci/vespa-ci-internal.sh" "$DOCKER_IMAGE" \
   "$GIT_COMMIT" "$LOG_PREFIX" 2>&1 | tee logs/${LOG_PREFIX}.log

# Needed because of piping docker run to tee above
exit ${PIPESTATUS[0]}
