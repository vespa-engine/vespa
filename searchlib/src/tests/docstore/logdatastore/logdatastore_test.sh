#!/bin/bash
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
truncate --size 3830 bug-7257706-truncated/1422358701368384000.idx
fail=0
VESPA_LOG_TARGET=file:vlog2.txt $VALGRIND ./searchlib_logdatastore_test_app || fail=1
rm -rf bug-7257706-truncated dangling-test
exit $fail
