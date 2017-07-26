#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
set -m

if [ $# -ne 4 ]; then
  echo "Usage: $0 <source dir> <build dir> <log dir> <num threads>"
  echo "This script should not be called manually."
  exit 1
fi

SOURCE_DIR=$1
BUILD_DIR=$2
LOG_DIR=$3
NUM_THREADS=$4

function bootstrap {
    source /opt/rh/devtoolset-6/enable || true
    export MAVEN_OPTS="-Xms128m -Xmx512m"
    sh ./bootstrap.sh full
    cmake3 -DCMAKE_INSTALL_PREFIX=/opt/vespa \
        -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
        -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
        -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
        -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
        -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
        -DVALGRIND_UNIT_TESTS=no \
        "${SOURCE_DIR}"
}

function build_java {
    cd "${SOURCE_DIR}"
    mvn install -nsu -B -T ${NUM_THREADS} -V # Should ideally split out test phase, but some unit tests fails on 'mvn test'
}

function build_cpp {
    cd "${BUILD_DIR}"
    make -j ${NUM_THREADS}
    ctest3 -j ${NUM_THREADS}
}

bootstrap

pids=()
set -o pipefail

# Java build and test
# Should be waited for first, because it takes much shorter time than C++ build.
build_java 2>&1 | tee ${LOG_DIR}/java.log &
pids+=($!)

# C++ build and test
build_cpp 2>&1 | tee ${LOG_DIR}/cpp.log &
pids+=($!)

EXIT_CODE=0
for pid in "${pids[@]}"; do
    wait $pid || EXIT_CODE=$?
    echo "$pid finished with status ${EXIT_CODE}"
    if [[ "${EXIT_CODE}" != 0 ]]; then
        echo "Exiting and killing remaining jobs."
        break;
    fi
done
set +o pipefail

# Kill any remaining jobs, ignoring error when no jobs are running
kill $(jobs -p) 2>/dev/null || true

exit ${EXIT_CODE}
