#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

ver="java8u20"

$BINREF/runjava CasingVariants > out.txt
./lowercasing_test_casingvariants_fastlib_app $SOURCE_DIRECTORY/letters $SOURCE_DIRECTORY/ref.txt.$ver > out.fastlib.txt
./lowercasing_test_casingvariants_vespalib_app $SOURCE_DIRECTORY/letters $SOURCE_DIRECTORY/ref.txt.$ver > out.vespalib.txt

echo "Verify Java"
if ! diff -u out.txt $SOURCE_DIRECTORY/ref.txt.$ver; then
    exit 1
fi

echo "Verify fastlib"
if ! diff -u out.fastlib.txt $SOURCE_DIRECTORY/ref.fastlib.txt.$ver; then
    exit 1
fi

echo "Verify vespalib"
if ! diff -u out.vespalib.txt $SOURCE_DIRECTORY/ref.vespalib.txt.$ver; then
    exit 1
fi
