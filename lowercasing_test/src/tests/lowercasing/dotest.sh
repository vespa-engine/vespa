#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

ver="java"$(java -version 2>&1 | perl -ne 'm/version "(\d+)[.]/ and print $1 . "\n"')

$BINREF/compilejava -d . $SOURCE_DIRECTORY/Letters.java
$BINREF/compilejava -d . $SOURCE_DIRECTORY/CasingVariants.java

$BINREF/runjava Letters letters > letters.${ver}
$BINREF/runjava Letters lower > curr-lower.${ver}
$BINREF/runjava Letters lower-drop > curr-ldrop.${ver}

./lowercasing_test_casingvariants_fastlib_app  letters.${ver} curr-lower.${ver} > out.fastlib.txt
./lowercasing_test_casingvariants_vespalib_app letters.${ver} curr-lower.${ver} > out.vespalib.txt

echo "Verify Java"
$BINREF/runjava CasingVariants letters.${ver} > out.casing.txt
diff --label casing-diff.exp --label casing-diff.act -u curr-lower.${ver} out.casing.txt > diff.casing.txt || true

if ! diff -u diff.casing.txt $SOURCE_DIRECTORY/ref.casing.txt.$ver; then
    echo "Unexpected diff: builtin java vs vespa lowercasing"
    exit 1
fi

echo "Verify fastlib"
if ! diff -u out.fastlib.txt $SOURCE_DIRECTORY/ref.fastlib.txt.$ver; then
    echo "Unexpected diff: builtin java vs fastlib lowercasing"
    exit 1
fi

echo "Verify vespalib"
if ! diff -u out.vespalib.txt $SOURCE_DIRECTORY/ref.vespalib.txt.$ver; then
    echo "Unexpected diff: builtin java vs vespalib lowercasing"
    exit 1
fi
