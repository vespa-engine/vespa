#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

. ../../binref/env.sh
export PORT_2
sh $BINREF/progctl.sh progdefs.sh start javaserver 1
$BINREF/runjava PollRPCServer tcp/localhost:$PORT_2
$VALGRIND ./jrt_test_echo-client_app tcp/localhost:$PORT_2 > out.txt
sh $BINREF/progctl.sh progdefs.sh stop javaserver 1

if diff -u out.txt ref.txt; then
    exit 0
else
    exit 1
fi
