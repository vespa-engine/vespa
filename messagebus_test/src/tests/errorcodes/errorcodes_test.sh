#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava $SOURCE_DIRECTORY/DumpCodes.java

./messagebus_test_dumpcodes_app > cpp-dump.txt
$BINREF/runjava DumpCodes > java-dump.txt
diff -u $SOURCE_DIRECTORY/ref-dump.txt cpp-dump.txt
diff -u $SOURCE_DIRECTORY/ref-dump.txt java-dump.txt
