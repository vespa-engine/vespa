#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

fail=0

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh start javaserver 1 || fail=1

./jrt_test_extract-reflection_app tcp/localhost:$PORT_1 verbose > out.txt || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop javaserver 1 || fail=1

if diff -u out.txt $SOURCE_DIRECTORY/ref.txt; then
    exit $fail
else
    exit 1
fi
