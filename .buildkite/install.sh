#!/bin/bash

set -euo pipefail

make -j "$NUM_CPU_LIMIT" install DESTDIR="$WORKDIR/vespa-install"

# The cmake install does not handle install into /usr/share/man. Do it explicitly here.
mkdir -p "$WORKDIR/vespa-install/usr/share/man/man1"
"$WORKDIR/vespa-install/opt/vespa/bin/vespa" man "$WORKDIR/vespa-install/usr/share/man/man1"
