#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

. ../../binref/env.sh

ret=true

export PORT_8
sh $BINREF/progctl.sh progdefs.sh start slobrok 1
${BINREF}/runjava SlobrokAPITest tcp/localhost:${PORT_8} || ret=false
${BINREF}/sbcmd ${PORT_8} slobrok.system.stop
sh $BINREF/progctl.sh progdefs.sh stop slobrok 1

$ret
