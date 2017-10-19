#!/bin/bash -e
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

usage() {
    echo "Usage: $0 <source-dir> <build-dir>" >&2
}

if [ $# -eq 2 ]; then
    SOURCE_DIR=$(realpath $1)
    BUILD_DIR=$(realpath $2)
elif [[ $# -eq 1 && ( "$1" = "-h" || "$1" = "--help" )]]; then
    usage
    exit 0
else
    echo "Wrong number of arguments: expected 2, was $#" >&2
    usage
    exit 1
fi

mkdir -p "${BUILD_DIR}"

source /opt/rh/devtoolset-6/enable || true
cd "${SOURCE_DIR}"
sh ./bootstrap.sh full
cd "${BUILD_DIR}"
sh ${SOURCE_DIR}/bootstrap-cmake.sh ${SOURCE_DIR}
