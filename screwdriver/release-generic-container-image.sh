#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_VERSION=$1

TMPDIR=$(mktemp -d)
#trap "rm -rf $TMPDIR" EXIT

pushd $TMPDIR
git clone -q --filter tree:0 https://github.com/vespa-engine/vespa
(cd vespa && git checkout v$VESPA_VERSION)
(cd vespa && screwdriver/replace-vespa-version-in-poms.sh $VESPA_VERSION $(pwd) )

make -C vespa -f .copr/Makefile srpm outdir=$(pwd)

rpmbuild --rebuild \
  --define="_topdir $TMPDIR/vespa-rpmbuild" \
  --define "debug_package %{nil}" \
  --define "_debugsource_template %{nil}" \
  *.src.rpm

#  --define '_cmake_extra_opts "-DDEFAULT_VESPA_CPU_ARCH_FLAGS=-msse3 -mcx16 -mtune=intel"' \

rm -f *.src.rpm
mv $TMPDIR/vespa-rpmbuild/RPMS/*/*.rpm .

cat <<EOF > Dockerfile
ARG VESPA_VERSION
FROM docker.io/vespaengine/vespa:\$VESPA_VERSION
USER root
RUN --mount=type=bind,target=/rpms/,source=. dnf reinstall -y /rpms/vespa*rpm && dnf clean all
USER vespa
EOF

readonly IMAGE_NAME="vespaengine/vespa-generic-intel-x86_64"

docker build --progress plain \
  --build-arg VESPA_VERSION=$VESPA_VERSION \
  --tag docker.io/$IMAGE_NAME:$VESPA_VERSION \
  --tag docker.io/$IMAGE_NAME:latest \
  --tag vespaengine/vespa:latest \
  --file Dockerfile .

vespa/screwdriver/test-quick-start-guide.sh

if curl -fsSL https://hub.docker.com/v2/repositories/$IMAGE_NAME/tags/$VESPA_VERSION/ &> /dev/null; then
  echo "Container image docker.io/$IMAGE_NAME:$VESPA_VERSION aldready exists."
else
  OPT_STATE="$(set +o)"
  set +x
  docker login --username aressem --password "$DOCKER_HUB_DEPLOY_TOKEN"
  eval "$OPT_STATE"
  docker push docker.io/$IMAGE_NAME:$VESPA_VERSION
  docker push docker.io/$IMAGE_NAME:latest
fi

