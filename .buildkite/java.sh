#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Builds Java components using Maven.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

mydir=${0%/*}
shlim=${mydir}/show-limits.sh
if [ -x "${shlim}" ]; then
    "${shlim}" || echo "failed: ${shlim}"
fi

echo "--- ☕ Building Java components"
# shellcheck disable=1091
source /etc/profile.d/enable-gcc-toolset.sh

PATH=/opt/vespa-deps/bin:$PATH

cd "$SOURCE_DIR"

echo "Running Maven build with target: ${VESPA_MAVEN_TARGET} [threads: ${NUM_MVN_THREADS} opts: ${MAVEN_OPTS:-none} extra-opts: ${VESPA_MAVEN_EXTRA_OPTS:-none}]"
read -ra MVN_EXTRA_OPTS <<< "$VESPA_MAVEN_EXTRA_OPTS"
./mvnw -T "$NUM_MVN_THREADS" "${MVN_EXTRA_OPTS[@]}" "$VESPA_MAVEN_TARGET"
