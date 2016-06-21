#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
fi
VESPA_VERSION=$1

cd /vespa
./dist.sh ${VESPA_VERSION}
rpmbuild -bb ~/rpmbuild/SPECS/vespa-${VESPA_VERSION}.spec
cp -a ~/rpmbuild/RPMS/x86_64/*.rpm /vespa/docker

