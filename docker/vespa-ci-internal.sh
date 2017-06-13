#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git commit>"
  echo "This script should not be called manually."
  exit 1
fi

GIT_COMMIT=$1
SOURCE_DIR=/home/vespabuilder/vespa
BUILD_DIR=/home/vespabuilder/build
THREADS=$(nproc --all)

mkdir "${SOURCE_DIR}"
mkdir "${BUILD_DIR}"
git --work-tree="${SOURCE_DIR}" --git-dir=/vespa/.git checkout ${GIT_COMMIT} -- .
cd "${SOURCE_DIR}"
source /opt/rh/devtoolset-6/enable || true
sh ./bootstrap.sh full
cmake3 -DCMAKE_INSTALL_PREFIX=/opt/vespa \
      -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
      -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
      -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
      -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
      -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
      "${BUILD_DIR}"
mvn install
make -j ${THREADS}
make -j ${THREADS} test
