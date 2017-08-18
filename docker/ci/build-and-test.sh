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

function build_java {
    cd "${SOURCE_DIR}"
    export MAVEN_OPTS="-Xms128m -Xmx512m"
    mvn install -nsu -B -T ${NUM_THREADS}  # Should ideally split out test phase, but some unit tests fails on 'mvn test'
}

function build_cpp {
    cd "${BUILD_DIR}"
    make -j ${NUM_THREADS}
    ctest3 -j ${NUM_THREADS}
}

bash ${SOURCE_DIR}/bootstrap-cpp.sh ${SOURCE_DIR} ${BUILD_DIR}

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
