#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

cd "$SOURCE_DIR"

make -C client/go BIN="$WORKDIR/vespa-install/opt/vespa/bin" SHARE="$WORKDIR/vespa-install/usr/share" install-all
