#!/bin/bash -e
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

usage() {
    echo "Usage: $0 [-u] <source-dir> [<extra-cmake-args>]" >&2
}

UNPRIVILEGED=false
while getopts "uh" opt; do
    case "${opt}" in
        u)
            UNPRIVILEGED=true
            ;;
        h)
            usage
            exit 0
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

if [[ $# -eq 0 ]]; then
    SOURCE_DIR=$(dirname "$0")
    EXTRA_CMAKE_ARGS=""
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

if [ -z "$VESPA_LLVM_VERSION" ]; then
    VESPA_LLVM_VERSION=5.0
fi

if $UNPRIVILEGED; then
    VESPA_INSTALL_PREFIX="$HOME/vespa"
    UNPRIVILEGED_ARGS="-DVESPA_USER=$(id -un) -DVESPA_UNPRIVILEGED=yes"
else
    VESPA_INSTALL_PREFIX="/opt/vespa"
    UNPRIVILEGED_ARGS="-DVESPA_UNPRIVILEGED=no"
fi

cmake3 \
    -DCMAKE_INSTALL_PREFIX=${VESPA_INSTALL_PREFIX} \
    -DJAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-openjdk} \
    -DCMAKE_PREFIX_PATH="/opt/vespa-deps" \
    -DEXTRA_LINK_DIRECTORY="/opt/vespa-deps/lib64;/usr/lib64/llvm$VESPA_LLVM_VERSION/lib" \
    -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-deps/include;/usr/include/llvm$VESPA_LLVM_VERSION" \
    -DCMAKE_INSTALL_RPATH="${VESPA_INSTALL_PREFIX}/lib64;/opt/vespa-deps/lib64;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/lib64/llvm$VESPA_LLVM_VERSION/lib" \
    ${UNPRIVILEGED_ARGS} \
    ${EXTRA_CMAKE_ARGS} \
    -DVESPA_LLVM_VERSION=$VESPA_LLVM_VERSION \
    "${SOURCE_DIR}"
