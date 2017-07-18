#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
set -m
set -x

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  echo "This script should not be called manually."
  exit 1
fi

GIT_COMMIT=$1
SOURCE_DIR=~/vespa
BUILD_DIR=~/build
LOG_DIR=~/log
NUM_CORES=$(nproc --all)
NUM_THREADS=$((${NUM_CORES} * 2))

function build_java {
    cd "${SOURCE_DIR}"
    mvn install -nsu -B -T 2.0C -V -DskipTests=true -Dmaven.javadoc.skip=true
    mvn install -nsu -B -T 2.0C -V
}

function build_cpp {
    cd "${BUILD_DIR}"
    cmake3 -DCMAKE_INSTALL_PREFIX=/opt/vespa \
        -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
        -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
        -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
        -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
        -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
        -DVALGRIND_UNIT_TESTS=no \
        "${SOURCE_DIR}"
    make -j ${NUM_THREADS}
    ctest3 -j ${NUM_THREADS}
}

mkdir "${SOURCE_DIR}"
mkdir "${BUILD_DIR}"
mkdir "${LOG_DIR}"

git clone --no-checkout --no-hardlinks file:///vespa "${SOURCE_DIR}"
cd "${SOURCE_DIR}"
git -c advice.detachedHead=false checkout ${GIT_COMMIT}
source /opt/rh/devtoolset-6/enable || true

export MAVEN_OPTS="-Xms128m -Xmx512m"
sh ./bootstrap.sh full

pids=()
set -o pipefail
TIMESTAMP=$(date +%Y-%m-%dT%H:%M:%S)

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

cp ${LOG_DIR}/java.log /vespa/docker/vespa-ci-java-${TIMESTAMP}.log
cp ${LOG_DIR}/cpp.log /vespa/docker/vespa-ci-cpp-${TIMESTAMP}.log

exit ${EXIT_CODE}
