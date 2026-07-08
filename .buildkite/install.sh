#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Installs Vespa components and man pages.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi
: "${NUM_CPU_LIMIT:?Environment variable NUM_CPU_LIMIT must be set (CPU limit)}"
: "${WORKDIR:?Environment variable WORKDIR must be set (working directory)}"
echo "--- 📦 Installing Vespa components"
echo "Running make install with $NUM_CPU_LIMIT parallel jobs..."
make -j "$NUM_CPU_LIMIT" install DESTDIR="$WORKDIR/vespa-install"

echo "Installing man pages..."
# The cmake install does not handle install into /usr/share/man. Do it explicitly here.
mkdir -p "$WORKDIR/vespa-install/usr/share/man/man1"
"$WORKDIR/vespa-install/opt/vespa/bin/vespa" man "$WORKDIR/vespa-install/usr/share/man/man1"
