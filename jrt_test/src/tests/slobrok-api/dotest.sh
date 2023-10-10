#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

fail=0

. ../../binref/env.sh

export PORT_8
$BINREF/progctl.sh progdefs.sh start slobrok 1
$BINREF/runjava SlobrokAPITest tcp/localhost:${PORT_8} || fail=1
$SBCMD ${PORT_8} slobrok.system.stop
$BINREF/progctl.sh progdefs.sh stop slobrok 1

exit $fail

