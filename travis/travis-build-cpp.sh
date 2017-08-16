#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

SOURCE_DIR=/source
BUILD_DIR=~/build

mkdir "${BUILD_DIR}"

ccache -M 20G
NUM_THREADS=4
source /opt/rh/devtoolset-6/enable || true
cd "${SOURCE_DIR}"
sh ./bootstrap.sh full
cd "${BUILD_DIR}"
cmake3 \
    -DCMAKE_INSTALL_PREFIX=/opt/vespa \
    -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
    -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
    -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
    -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
    -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
    -DVALGRIND_UNIT_TESTS=no \
    "${SOURCE_DIR}"
make -j ${NUM_THREADS}
ctest3 --output-on-failure -j ${NUM_THREADS}

