#!/bin/bash

set -euo pipefail

cd "$SOURCE_DIR"
make -j "$NUM_CPP_THREADS"
