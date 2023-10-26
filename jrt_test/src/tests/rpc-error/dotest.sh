#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

fail=0

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh start cppserver 1 || fail=1
$BINREF/runjava PollRPCServer tcp/localhost:$CPP_PORT || fail=1

echo "CPP CLIENT <-> CPP SERVER"
./jrt_test_test-errors_app tcp/localhost:$CPP_PORT || fail=1

echo "JAVA CLIENT <-> CPP SERVER"
$BINREF/runjava TestErrors tcp/localhost:$CPP_PORT || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop cppserver 1
$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh start javaserver 1
$BINREF/runjava PollRPCServer tcp/localhost:$JAVA_PORT || fail=1

echo "CPP CLIENT <-> JAVA SERVER"
./jrt_test_test-errors_app tcp/localhost:$JAVA_PORT || fail=1

echo "JAVA CLIENT <-> JAVA SERVER"
$BINREF/runjava TestErrors tcp/localhost:$JAVA_PORT || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop javaserver 1

if [ $fail = "0" ]; then
    echo "OK"
else
    echo "FAIL"
fi
exit $fail
