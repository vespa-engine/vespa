#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

. ../../binref/env.sh

./lowercasing_test_hello-world-local_app > out.txt
$HELLO_WORLD_APP                >> out.txt
$BINREF/runjava HelloWorldLocal >> out.txt
$BINREF/runjava HelloWorld      >> out.txt

if diff -u out.txt ref.txt; then
    exit 0
else
    exit 1
fi
