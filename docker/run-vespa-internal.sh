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

yum localinstall -y $(ls vespa*-${VESPA_VERSION}-*.rpm | xargs)

# Workaround until we figure out why rpm does not set the ownership.
chown -R vespa:vespa /opt/vespa

export VESPA_CONFIG_SERVERS=$(hostname)

/opt/vespa/bin/vespa-start-configserver
/opt/vespa/bin/vespa-start-services

# Print log forever
while true; do
  /opt/vespa/bin/logfmt -f /opt/vespa/logs/vespa/vespa.log
  sleep 10
done
