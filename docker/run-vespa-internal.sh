#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
  echo "This script should not be called manually."
  exit 1
fi

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

VESPA_VERSION=$1

rpm -i "vespa-${VESPA_VERSION}-1.el7.centos.x86_64.rpm"
rpm -i "vespa-debuginfo-${VESPA_VERSION}-1.el7.centos.x86_64.rpm"

# Workaround until we figure out why rpm does not set the ownership.
chown -R vespa:vespa /opt/vespa

/opt/vespa/bin/vespa-start-configserver
/opt/vespa/bin/vespa-start-services

# Sleep until killed
tail -f /dev/null
