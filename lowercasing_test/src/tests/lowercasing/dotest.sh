#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

. ../../binref/env.sh

ver="java8u20"

$BINREF/runjava CasingVariants > out.txt
./lowercasing_test_casingvariants_fastlib_app ./letters ref.txt.$ver > out.fastlib.txt
./lowercasing_test_casingvariants_vespalib_app ./letters ref.txt.$ver > out.vespalib.txt

echo "Verify Java"
if ! diff -u out.txt ref.txt.$ver; then
    exit 1
fi

echo "Verify fastlib"
if ! diff -u out.fastlib.txt ref.fastlib.txt.$ver; then
    exit 1
fi

echo "Verify vespalib"
if ! diff -u out.vespalib.txt ref.vespalib.txt.$ver; then
    exit 1
fi
