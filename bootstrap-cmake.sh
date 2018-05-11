#!/bin/bash -e
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

usage() {
    echo "Usage: $0 <source-dir> [<extra-cmake-args>]" >&2
}

if [[ $# -eq 1 && ( "$1" = "-h" || "$1" = "--help" )]]; then
    usage
    exit 0
elif [[ $# -eq 1 ]]; then
    SOURCE_DIR=$1
    EXTRA_CMAKE_ARGS=""
elif [ $# -eq 2 ]; then
    SOURCE_DIR=$1
    EXTRA_CMAKE_ARGS=$2
else
    echo "Wrong number of arguments: expected 1 or 2, was $#" >&2
    usage
    exit 1
fi

cmake3 \
    -DCMAKE_INSTALL_PREFIX=/opt/vespa \
    -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
    -DEXTRA_LINK_DIRECTORY="/opt/vespa-gtest/lib;/opt/vespa-boost/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
    -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-gtest/include;/opt/vespa-boost/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
    -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-gtest/lib;/opt/vespa-boost/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/lib64/llvm3.9/lib" \
    ${EXTRA_CMAKE_ARGS} \
    "${SOURCE_DIR}"
