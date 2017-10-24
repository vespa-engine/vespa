#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

export SOURCE_DIR=/source
export NUM_THREADS=4
source /etc/profile.d/devtoolset-6.sh || true

ccache --max-size=1250M
ccache --set-config=compression=true
ccache --print-config

cd ${SOURCE_DIR}
sh ./bootstrap.sh java
mvn install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS}
bash ${SOURCE_DIR}/bootstrap-cmake.sh ${SOURCE_DIR}
make -j ${NUM_THREADS}
ctest3 --output-on-failure -j ${NUM_THREADS}
ccache --show-stats
make install
