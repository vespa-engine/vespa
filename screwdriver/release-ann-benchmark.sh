#!/usr/bin/ssh-agent /bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1

if [[ -z "$ANN_BENCHMARK_DEPLOY_KEY" ]]; then
    echo "Environment variable ANN_BENCHMARK_DEPLOY_KEY must be set, but is empty."
    exit 1
fi

BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT
cd $BUILD_DIR

ssh-add -D
ssh-add <(echo $ANN_BENCHMARK_DEPLOY_KEY | base64 -d)
git clone git@github.com:vespa-engine/vespa-ann-benchmark
cd vespa-ann-benchmark

RELEASE_TAG="v$VESPA_VERSION"
if ! git rev-parse $RELEASE_TAG &> /dev/null; then
    git tag -a "$RELEASE_TAG" -m "Release version $VESPA_VERSION"
    git push origin "$RELEASE_TAG"
fi

