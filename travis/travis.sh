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

DOCKER_IMAGE=vespaengine/vespa-build-centos7:latest

bell &
docker run --rm -v ${HOME}/.m2:/root/.m2 -v ${HOME}/.ccache:/root/.ccache -v $(pwd):/source \
       -e TRAVIS_REPO_SLUG=$TRAVIS_REPO_SLUG -e TRAVIS_PULL_REQUEST=$TRAVIS_PULL_REQUEST \
       --entrypoint /source/travis/travis-build.sh ${DOCKER_IMAGE}
exit $?
