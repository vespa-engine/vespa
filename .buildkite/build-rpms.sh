#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Builds RPM packages, including creating source RPMs, building binary RPMs, and preparing a repository.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

: "${VESPA_VERSION:?Environment variable VESPA_VERSION must be set (version to build)}"
: "${LOCAL_RPM_REPO:?Environment variable LOCAL_RPM_REPO must be set (path to local RPM repo)}"

echo "--- 📦 Building RPM packages"
ulimit -c 0

echo "Creating source RPM..."
make  -f .copr/Makefile srpm outdir="$WORKDIR"

echo "Building binary RPMs..."
rpmbuild --rebuild \
  --define="_topdir $WORKDIR/vespa-rpmbuild" \
  --define "_debugsource_template %{nil}" \
  --define "_binary_payload w10T4.zstdio" \
  --define "installdir $WORKDIR/vespa-install" "$WORKDIR"/vespa-"$VESPA_VERSION"-*.src.rpm

echo "Moving RPMs and creating repository..."
mv "$WORKDIR"/vespa-rpmbuild/RPMS/*/*.rpm "$LOCAL_RPM_REPO"
createrepo "$LOCAL_RPM_REPO"
