#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

export VESPA_LOG_TARGET=file:vespa.log

rm -rf vespa.log*

./logd_dummyserver_app &
echo $! > dummyserver.pid

sleep 5

$SOURCE_DIRECTORY/create_configfile.sh

export VESPA_CONFIG_ID=file:logd.cfg
../../apps/logd/vespa-logd &
echo $! > logd.pid

./logd_dummylogger_app

echo "stopping servers..."
cat *.pid | xargs kill

echo "looking for incomplete log forwarding due to slow log server..."
if grep incomplete vespa.log*; then
    echo "OK"
    exit 0
else
    echo "FAIL"
    exit 1
fi
