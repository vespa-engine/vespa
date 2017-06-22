#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
set -x

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  echo "This script should not be called manually."
  exit 1
fi

GIT_COMMIT=$1
SOURCE_DIR=~/vespa
BUILD_DIR=~/build
NUM_CORES=$(nproc --all)
NUM_THREADS=$((${NUM_CORES} * 2))

mkdir "${SOURCE_DIR}"
mkdir "${BUILD_DIR}"
git clone --no-checkout --no-hardlinks file:///vespa "${SOURCE_DIR}"
cd "${SOURCE_DIR}"
git -c advice.detachedHead=false checkout ${GIT_COMMIT}
source /opt/rh/devtoolset-6/enable || true
sh ./bootstrap.sh full
MAVEN_OPTS="-Xms128m -Xmx512m" mvn -T ${NUM_THREADS} install
cd "${BUILD_DIR}"
cmake3 -DCMAKE_INSTALL_PREFIX=/opt/vespa \
      -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
      -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
      -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
      -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
      -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
      "${SOURCE_DIR}"
make -j ${NUM_THREADS}
ctest3 -j ${NUM_THREADS}
