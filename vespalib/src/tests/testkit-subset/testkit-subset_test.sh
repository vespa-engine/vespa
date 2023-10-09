#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

TEST_SUBSET=pass $VALGRIND ./vespalib_testkit-subset_test_app 2> out.txt
TEST_SUBSET="extra\.cpp:.*pass.*" $VALGRIND ./vespalib_testkit-subset_test_app 2>> out.txt
cat out.txt | grep "\.cpp: " > out.relpath.txt
cmp -s out.relpath.txt $SOURCE_DIRECTORY/out.ref.2.txt && exit 0
diff -u out.relpath.txt $SOURCE_DIRECTORY/out.ref.txt
