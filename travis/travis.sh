#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

PLATFORM_LANGUAGE=$1
DOCKER_IMAGE=vespaengine/vespa-dev:latest
docker run --rm -v ${HOME}/.m2:/root/.m2 -v ${HOME}/.ccache:/root/.ccache -v $(pwd):/source \
           --entrypoint /source/travis/travis-build-${PLATFORM_LANGUAGE}.sh ${DOCKER_IMAGE}

