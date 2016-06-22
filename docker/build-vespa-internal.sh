#!/bin/bash
set -e

if [ $# -ne 3 ]; then
  echo "Usage: $0 <vespa version> <caller uid> <caller gid>"
  echo "This script should not be called manually."
  exit 1
fi
VESPA_VERSION=$1
CALLER_UID=$2
CALLER_GID=$3

cd /vespa
./dist.sh ${VESPA_VERSION}
rpmbuild -bb ~/rpmbuild/SPECS/vespa-${VESPA_VERSION}.spec
chown ${CALLER_UID}:${CALLER_GID} ~/rpmbuild/RPMS/x86_64/*.rpm
mv ~/rpmbuild/RPMS/x86_64/*.rpm /vespa/docker 

