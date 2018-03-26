#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 0 ]; then
  echo "Usage: $0"
  echo "This script should not be called manually."
  exit 1
fi

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

# Workaround until we figure out why rpm does not set the ownership.
chown -R vespa:vespa /opt/vespa

export VESPA_CONFIGSERVERS=$(hostname)

/opt/vespa/bin/vespa-start-configserver
# Give config server some time to come up before starting services
sleep 5
/opt/vespa/bin/vespa-start-services

# Print log forever
while true; do
  /opt/vespa/bin/vespa-logfmt -f /opt/vespa/logs/vespa/vespa.log
  sleep 10
done
