#!/bin/bash

set -euo pipefail

ctest3 --output-junit "$LOG_DIR/vespa-cpptest-results.xml" --output-on-failure -j "$NUM_CPU_LIMIT"
