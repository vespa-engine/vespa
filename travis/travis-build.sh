#!/bin/bash
# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

export SOURCE_DIR=/source
export NUM_THREADS=6
export MALLOC_ARENA_MAX=1
export MAVEN_OPTS="-Xss1m -Xms128m -Xmx2g"
source /etc/profile.d/enable-devtoolset-9.sh
source /etc/profile.d/enable-rh-maven35.sh

ccache --max-size=1600M
ccache --set-config=compression=true
ccache -p

if ! source /source/travis/detect-what-to-build.sh; then
    echo "Could not detect what to build."
    SHOULD_BUILD=all
fi

echo "Building: $SHOULD_BUILD"

cd ${SOURCE_DIR}

case $SHOULD_BUILD in
  cpp)
    env VESPA_MAVEN_EXTRA_OPTS="--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}" sh ./bootstrap.sh full
    cmake3 -DVESPA_UNPRIVILEGED=no .
    make -j ${NUM_THREADS}
    ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    ;;
  java)
    env VESPA_MAVEN_EXTRA_OPTS="--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}" sh ./bootstrap.sh java
    mvn -V install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS}
    ;;
  *)
    env VESPA_MAVEN_EXTRA_OPTS="--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}" sh ./bootstrap.sh java
    mvn -V install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS}
    cmake3 -DVESPA_UNPRIVILEGED=no .
    make -j ${NUM_THREADS}
    ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    make install
    ;;    
esac


