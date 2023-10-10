#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

read port < logserver.port

cat > logd.cfg << EOF
logserver.host "localhost"
logserver.port $port
rotate.size 1024
rotate.age 1000
remove.totalmegabytes 100
remove.age 30
EOF
