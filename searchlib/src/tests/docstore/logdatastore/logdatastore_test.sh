#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

cp -r $SOURCE_DIRECTORY/bug-7257706 bug-7257706-truncated
mkdir -p dangling-test
cp -a $SOURCE_DIRECTORY/bug-7257706/*.dat dangling-test/
cp -a $SOURCE_DIRECTORY/bug-7257706/*.idx dangling-test/
cp -a $SOURCE_DIRECTORY/dangling/*.dat dangling-test/
cp -a $SOURCE_DIRECTORY/dangling/*.idx dangling-test/

mkdir -p incompletecompact-test
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.dat incompletecompact-test/
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.idx incompletecompact-test/
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.dat incompletecompact-test/2000000000000000000.dat
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.idx incompletecompact-test/2000000000000000000.idx
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.dat incompletecompact-test/2000000000000000001.dat
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.idx incompletecompact-test/2000000000000000001.idx
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.dat incompletecompact-test/2422358701368384000.dat
cp -a $SOURCE_DIRECTORY/bug-7257706/1422358701368384000.idx incompletecompact-test/2422358701368384000.idx

fail=0
VESPA_LOG_TARGET=file:vlog2.txt $VALGRIND ./searchlib_logdatastore_test_app || fail=1
rm -rf bug-7257706-truncated dangling-test incompletecompact-test
exit $fail
