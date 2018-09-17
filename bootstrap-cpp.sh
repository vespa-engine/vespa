#!/bin/bash -e
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

usage() {
    echo "Usage: $0 [-u] <source-dir> <build-dir>" >&2
}

UNPRIVILEGED=""
while getopts "uh" opt; do
    case "${opt}" in
        u)
            UNPRIVILEGED="-u"
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

# Parse arguments
if [ $# -eq 2 ]; then
    SOURCE_DIR="$1"
    BUILD_DIR="$2"
else
    echo "Wrong number of arguments: expected 2, was $#" >&2
    usage
    exit 1
fi

# Check the source directory
if [ ! -d "$SOURCE_DIR" ] ; then
    echo "Source dir $SOURCE_DIR not found" >&2
    exit 1
fi
SOURCE_DIR=$(realpath "${SOURCE_DIR}")

# Check (and possibly create) the build directory
mkdir -p "${BUILD_DIR}" || {
    echo "Failed to create build directory" >&2
    exit 1
}
BUILD_DIR=$(realpath "${BUILD_DIR}")

# Build it
source /opt/rh/devtoolset-7/enable || true
cd "${SOURCE_DIR}"
bash ./bootstrap.sh full
cd "${BUILD_DIR}"
bash ${SOURCE_DIR}/bootstrap-cmake.sh ${UNPRIVILEGED} "${SOURCE_DIR}"
