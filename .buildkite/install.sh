#!/bin/bash

set -euo pipefail

make -j "$NUM_CPU_LIMIT" install DESTDIR="$WORKDIR/vespa-install"
