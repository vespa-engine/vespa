#!/bin/bash
# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

readonly SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd )"
readonly NUM_THREADS=$(( $(nproc) + 2 ))

source /etc/profile.d/enable-devtoolset-9.sh
source /etc/profile.d/enable-rh-maven35.sh

export MALLOC_ARENA_MAX=1
export MAVEN_OPTS="-Xss1m -Xms128m -Xmx2g"
export VESPA_MAVEN_EXTRA_OPTS="${VESPA_MAVEN_EXTRA_OPTS:+${VESPA_MAVEN_EXTRA_OPTS} }--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}"

ccache --max-size=1600M
ccache --set-config=compression=true
ccache -p

if ! source $SOURCE_DIR/travis/detect-what-to-build.sh; then
    echo "Could not detect what to build."
    SHOULD_BUILD=all
fi

echo "Building: $SHOULD_BUILD"

cd ${SOURCE_DIR}

case $SHOULD_BUILD in
  cpp)
    ./bootstrap.sh full
    cmake3 -DVESPA_UNPRIVILEGED=no .
    make -j ${NUM_THREADS}
    ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    ;;
  java)
    ./bootstrap.sh java
    mvn -V $VESPA_MAVEN_EXTRA_OPTS install
    ;;
  *)
    ./bootstrap.sh java
    mvn -V $VESPA_MAVEN_EXTRA_OPTS install
    cmake3 -DVESPA_UNPRIVILEGED=no .
    make -j ${NUM_THREADS}
    ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    make install
    ;;    
esac


