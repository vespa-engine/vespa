#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

export SOURCE_DIR=/source
export NUM_THREADS=6
export MALLOC_ARENA_MAX=1
export MAVEN_OPTS="-Xss1m -Xms128m -Xmx2g"
source /etc/profile.d/enable-devtoolset-8.sh
source /etc/profile.d/enable-rh-maven35.sh

ccache --max-size=1250M
ccache --set-config=compression=true
ccache -p

cd ${SOURCE_DIR}
env VESPA_MAVEN_EXTRA_OPTS="--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}" sh ./bootstrap.sh java
mvn -V install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS}
bash ${SOURCE_DIR}/bootstrap-cmake.sh ${SOURCE_DIR}
make -j ${NUM_THREADS}
ctest3 --output-on-failure -j ${NUM_THREADS}
ccache --show-stats
make install
