#!/bin/bash
. ../../binref/env.sh

$BINREF/compilejava DumpCodes.java

./messagebus_test_dumpcodes_app > cpp-dump.txt
$BINREF/runjava DumpCodes > java-dump.txt
diff -u ref-dump.txt cpp-dump.txt
diff -u ref-dump.txt java-dump.txt
