#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

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
build_java 2>&1 | tee ${LOG_DIR}/java.log
build_cpp 2>&1 | tee ${LOG_DIR}/cpp.log
