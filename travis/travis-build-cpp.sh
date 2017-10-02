#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

SOURCE_DIR=/source
BUILD_DIR=~/build

mkdir "${BUILD_DIR}"

export CCACHE_MAXSIZE="1250M"
export CCACHE_COMPRESS=1
NUM_THREADS=4
ccache --print-config
source /etc/profile.d/devtoolset-6.sh || true

cd ${BUILD_DIR}
bash ${SOURCE_DIR}/bootstrap-cpp.sh ${SOURCE_DIR} ${BUILD_DIR}
make -j ${NUM_THREADS}
ctest3 --output-on-failure -j ${NUM_THREADS}
ccache --show-stats
