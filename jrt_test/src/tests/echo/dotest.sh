#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

fail=0

. ../../binref/env.sh
export PORT_2
$BINREF/progctl.sh progdefs.sh start javaserver 1
$BINREF/runjava PollRPCServer tcp/localhost:$PORT_2 || fail=1
$VALGRIND ./jrt_test_echo-client_app tcp/localhost:$PORT_2 > out.txt || fail=1
$BINREF/progctl.sh progdefs.sh stop javaserver 1

if diff -u out.txt ref.txt; then
    exit $fail
else
    exit 1
fi
