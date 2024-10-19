#!/bin/bash

set -euo pipefail

cd "$SOURCE_DIR"

make -C client/go BIN="$WORKDIR/vespa-install/opt/vespa/bin" SHARE="$WORKDIR/vespa-install/usr/share" install-all
