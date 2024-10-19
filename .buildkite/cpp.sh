#!/bin/bash

set -euo pipefail

source /etc/profile.d/enable-gcc-toolset.sh

cd "$SOURCE_DIR"
make -j "$NUM_CPP_THREADS"
