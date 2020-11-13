#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 3 ]; then
  echo "Usage: $0 <vespa version> <caller uid> <caller gid>"
  echo "This script should not be called manually."
  exit 1
fi
VESPA_VERSION=$1
CALLER_UID=$2
CALLER_GID=$3

if ! [ -x ./dist.sh ]; then
    echo ". is not a vespa-engine/vespa root directory"
    exit 1
fi

./dist.sh ${VESPA_VERSION}

yum -y install epel-release
yum -y install centos-release-scl

if ! yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo; then
  cat << 'EOF' > /etc/yum.repos.d/vespa-engine-stable.repo
[vespa-engine-stable]
name=vespa-engine-stable
baseurl=https://yahoo.bintray.com/vespa-engine/centos/$releasever/stable/$basearch
gpgcheck=0
repo_gpgcheck=0
enabled=1
EOF
fi

yum-builddep -y \
             --setopt="base-source.skip_if_unavailable=true" \
             --setopt="updates-source.skip_if_unavailable=true" \
             --setopt="extras-source.skip_if_unavailable=true" \
             --setopt="epel-source.skip_if_unavailable=true" \
             --setopt="centos-sclo-rh-source.skip_if_unavailable=true" \
             --setopt="centos-sclo-sclo-source.skip_if_unavailable=true" \
             ~/rpmbuild/SPECS/vespa-${VESPA_VERSION}.spec

rpmbuild -bb ~/rpmbuild/SPECS/vespa-${VESPA_VERSION}.spec
chown ${CALLER_UID}:${CALLER_GID} ~/rpmbuild/RPMS/x86_64/*.rpm
mv ~/rpmbuild/RPMS/x86_64/*.rpm docker
