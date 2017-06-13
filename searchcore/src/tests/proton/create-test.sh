#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

gen_ignore_file() {
    echo "generating '$1' ..."
    echo ".depend"       > $1
    echo "Makefile"     >> $1
    echo "${test}_test" >> $1
}

gen_project_file() {
    echo "generating '$1' ..."
    echo "APPLICATION ${test}_test"               > $1
    echo "OBJS $test"                            >> $1
    echo "EXTERNALLIBS searchlib document fnet"  >> $1
    echo "EXTERNALLIBS vespalib config vespalog" >> $1
    echo ""                                      >> $1
    echo "CUSTOMMAKE"                            >> $1
    echo "test: ${test}_test"                    >> $1
    echo -e "\t\$(VALGRIND) ./${test}_test"      >> $1
}

gen_source() {
    echo "generating '$1' ..."
    echo "#include <vespa/log/log.h>"                   > $1
    echo "LOG_SETUP(\"${test}_test\");"          >> $1
    echo "#include <vespa/vespalib/testkit/testapp.h>" >> $1
    echo ""                                      >> $1
    echo "// using namespace ;"                  >> $1
    echo ""                                      >> $1
    echo "TEST_SETUP(Test);"                     >> $1
    echo ""                                      >> $1
    echo "int"                                   >> $1
    echo "Test::Main()"                          >> $1
    echo "{"                                     >> $1
    echo "    TEST_INIT(\"${test}_test\");"      >> $1
    echo "    TEST_DONE();"                      >> $1
    echo "}"                                     >> $1
}

gen_desc() {
    echo "generating '$1' ..."
    echo "$test test. Take a look at $test.cpp for details." > $1
}

gen_file_list() {
    echo "generating '$1' ..."
    echo "$test.cpp" > $1
}

if [ $# -ne 1 ]; then
	echo "usage: $0 <name>"
	echo "  name: name of the test to create"
	exit 1
fi

test=$1
if [ -e $test ]; then
    echo "$test already present, don't want to mess it up..."
    exit 1
fi

echo "creating directory '$test' ..."
mkdir -p $test || exit 1
cd $test || exit 1
test=`basename $test`

gen_ignore_file  .cvsignore
gen_project_file fastos.project
gen_source       $test.cpp
gen_desc         DESC
gen_file_list    FILES
