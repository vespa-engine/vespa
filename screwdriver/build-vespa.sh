#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

readonly SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd )"
readonly NUM_THREADS=$(( $(nproc) + 2 ))

source /etc/profile.d/enable-gcc-toolset-11.sh

export MALLOC_ARENA_MAX=1
export MAVEN_OPTS="-Xss1m -Xms128m -Xmx2g"
export VESPA_MAVEN_EXTRA_OPTS="${VESPA_MAVEN_EXTRA_OPTS:+${VESPA_MAVEN_EXTRA_OPTS} }--no-snapshot-updates --batch-mode --threads ${NUM_THREADS}"

ccache --max-size=1600M
ccache --set-config=compression=true
ccache -p

if ! source $SOURCE_DIR/screwdriver/detect-what-to-build.sh; then
    echo "Could not detect what to build."
    SHOULD_BUILD=all
fi

echo "Building: $SHOULD_BUILD"

cd ${SOURCE_DIR}

case $SHOULD_BUILD in
  cpp)
    ./bootstrap.sh full
    cmake3 -DVESPA_UNPRIVILEGED=no .
    time make -j ${NUM_THREADS}
    time ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    ;;
  java)
    ./bootstrap.sh java
    mvn -V $VESPA_MAVEN_EXTRA_OPTS install
    ;;
  go)
    make -C client/go install-all
    ;;
  *)
    make -C client/go install-all
    ./bootstrap.sh java
    time mvn -V $VESPA_MAVEN_EXTRA_OPTS install
    cmake3 -DVESPA_UNPRIVILEGED=no .
    time make -j ${NUM_THREADS}
    time ctest3 --output-on-failure -j ${NUM_THREADS}
    ccache --show-stats
    make install
    ;;    
esac

if [[ $SHOULD_BUILD == systemtest ]]; then
  cd $HOME
  git clone https://github.com/vespa-engine/system-test
  export SYSTEM_TEST_DIR=$(pwd)/system-test
  export RUBYLIB="$SYSTEM_TEST_DIR/lib:$SYSTEM_TEST_DIR/tests"
  useradd vespa

  # Workaround for /opt/vespa/tmp directory created by systemtest runner
  mkdir -p /opt/vespa/tmp
  chmod 1777 /opt/vespa/tmp

  export USER=vespa
  $SYSTEM_TEST_DIR/lib/node_server.rb &
  NODE_SERVER_PID=$!
  sleep 3
  ruby $SYSTEM_TEST_DIR/tests/search/basicsearch/basic_search.rb || (/opt/vespa/bin/vespa-logfmt -N && false)
fi

