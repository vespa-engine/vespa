#!/bin/bash

set -euo pipefail

ulimit -c 0

make  -f .copr/Makefile srpm outdir="$WORKDIR"

rpmbuild --rebuild \
  --define="_topdir $WORKDIR/vespa-rpmbuild" \
  --define "_debugsource_template %{nil}" \
  --define "_binary_payload w10T.zstdio" \
  --define "installdir $WORKDIR/vespa-install" "$WORKDIR"/vespa-"$VESPA_VERSION"-*.src.rpm

mv "$WORKDIR"/vespa-rpmbuild/RPMS/*/*.rpm "$WORKDIR/artifacts/$ARCH/rpms"
createrepo "$WORKDIR/artifacts/$ARCH/rpms"
