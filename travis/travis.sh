#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

# Workaround for Travis log output timeout (jobs without output over 10 minutes are killed)
function bell() {
  while true; do
    echo "."
    sleep 300
  done
}

DOCKER_IMAGE=vespaengine/vespa-dev:vespa7-java11

bell &
docker run --rm -v ${HOME}/.m2:/root/.m2 -v ${HOME}/.ccache:/root/.ccache -v $(pwd):/source \
           --entrypoint /source/travis/travis-build-full.sh ${DOCKER_IMAGE}
exit $?
