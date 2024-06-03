#!/bin/bash

set -euo pipefail

set +e
if ! ctest3 --output-junit "$LOG_DIR/vespa-cpptest-results.xml" --output-on-failure -j "$NUM_CPP_THREADS" ; then
  #sleep 3600
  exit 1
fi

