#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

sh $BINREF/progctl.sh progdefs.sh start server 1

$BINREF/runjava PollRPCServer tcp/localhost:$PORT_0
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 aaa bbb >  out.txt
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 bbb ccc >> out.txt
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 ccc ddd >> out.txt
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 ddd eee >> out.txt
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 eee fff >> out.txt

sh $BINREF/progctl.sh progdefs.sh stop server 1

if diff -u out.txt ref.txt; then
    exit 0
else
    exit 1
fi
