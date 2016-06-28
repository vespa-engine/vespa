#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

fail=0

$BINREF/progctl.sh progdefs.sh start javaserver 1 || fail=1

./jrt_test_extract-reflection_app tcp/localhost:$PORT_1 verbose > out.txt || fail=1

$BINREF/progctl.sh progdefs.sh stop javaserver 1 || fail=1

if diff -u out.txt ref.txt; then
    exit $fail
else
    exit 1
fi
