#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

export VESPA_LOG_TARGET=file:vespa.log

rm -rf vespa.log*

./logd_dummyserver_app &
echo $! > dummyserver.pid

sleep 5

./create_configfile.sh


export VESPA_CONFIG_ID=file:logd.cfg
../../apps/logd/logd &
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
