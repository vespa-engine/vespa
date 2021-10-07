#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

echo "running pack test..."
./docsum-pack > packtest.out 2>&1
res=$?
if [ $res -eq 0 ]; then
    echo "pack test PASSED"
else
    echo "pack test FAILED!"
    echo "please check packtest.out"
    exit 1
fi
