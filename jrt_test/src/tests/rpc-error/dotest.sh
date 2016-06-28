#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

sh $BINREF/progctl.sh progdefs.sh start cppserver 1

$BINREF/runjava PollRPCServer tcp/localhost:$CPP_PORT
echo "CPP CLIENT <-> CPP SERVER"
./jrt_test_test-errors_app tcp/localhost:$CPP_PORT
if [ $? -ne 0 ]; then STATUS=fail; fi

echo "JAVA CLIENT <-> CPP SERVER"
$BINREF/runjava TestErrors tcp/localhost:$CPP_PORT
if [ $? -ne 0 ]; then STATUS=fail; fi

sh $BINREF/progctl.sh progdefs.sh stop cppserver 1


sh $BINREF/progctl.sh progdefs.sh start javaserver 1

$BINREF/runjava PollRPCServer tcp/localhost:$JAVA_PORT
echo "CPP CLIENT <-> JAVA SERVER"
./jrt_test_test-errors_app tcp/localhost:$JAVA_PORT
if [ $? -ne 0 ]; then STATUS=fail; fi

echo "JAVA CLIENT <-> JAVA SERVER"
$BINREF/runjava TestErrors tcp/localhost:$JAVA_PORT
if [ $? -ne 0 ]; then STATUS=fail; fi

sh $BINREF/progctl.sh progdefs.sh stop javaserver 1

if [ $STATUS = "ok" ]; then
    echo "OK"
else
    echo "FAIL"
    exit 1
fi
