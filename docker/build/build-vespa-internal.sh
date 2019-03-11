#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 5 ]; then
  echo "Usage: $0 <vespa version> <caller uid> <caller gid> <tmp build dir> <rpm dest dir>"
  echo "This script should not be called manually."
  exit 1
fi
VESPA_VERSION=$1
CALLER_UID=$2
CALLER_GID=$3
BUILD_DIR=$4
DEST_DIR=$5

if ! [ -x ./dist.sh ]; then
    echo ". is not a vespa-engine/vespa root directory"
    exit 1
fi

./dist.sh ${VESPA_VERSION}

yum -y install epel-release
yum -y install centos-release-scl

yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo

yum-builddep -y --setopt="centos-sclo-rh-source.skip_if_unavailable=true" $BUILD_DIR/SPECS/vespa-${VESPA_VERSION}.spec
rpmbuild -bb $BUILD_DIR/SPECS/vespa-${VESPA_VERSION}.spec
chown ${CALLER_UID}:${CALLER_GID} $BUILD_DIR/RPMS/x86_64/*.rpm
mv $BUILD_DIR/RPMS/x86_64/*.rpm $DEST_DIR
