#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
dirname=`dirname $0`
basedir=`readlink -f $dirname`
progname=`basename $0 .sh`
if [ $# -lt 3 ]; then
	echo "usage: $0 <rundir> <testlist> <top>"
	echo "  rundir: where to run and generate output"
	echo "  testlist: file with test names (relative to rundir)"
	echo "  top: path to top (relative to rundir)"
	exit 1
fi

rundir=$1
mkdir -p $1
cd $1 || exit 1
testlistfile=$2
testlist=`cat $2` || exit 1
top_path=$3
testmakefile=Makefile
rm -f $testmakefile

# Generate all target
echo "all:" >> $testmakefile
echo -e "\t\$(MAKE) clean" >> $testmakefile
echo -e "\t\$(MAKE) init" >> $testmakefile
echo -e "\t\$(MAKE) runtests" >> $testmakefile
echo -e "\t\$(MAKE) generate-report" >> $testmakefile

# Generate targets for each test
testnamelist=""
test_cnt=0
for test in $testlist; do
    test_cnt=$(($test_cnt + 1))

    testpath=$test
    case $test in
    \!*) testpath=${test#!};;
    \?*) testpath=${test#?};;
    esac
    test_name="test-"`echo $testpath | sed -e "s=^tests/==;s=/=.=g"`
    testnamelist="$testnamelist $test_name"

    echo "" >> $testmakefile
    echo "$test_name:" >> $testmakefile
    echo -e "\t@sh $basedir/run-test.sh . $test $test_cnt $top_path" >> $testmakefile
done

echo "" >> $testmakefile
echo "init:" >> $testmakefile
echo -e "\tdate +%s > tmp.start-time" >> $testmakefile
echo "" >> $testmakefile
echo -e "runtests:$testnamelist" >> $testmakefile

# Generate generate report target
echo "" >> $testmakefile
echo "generate-report:" >> $testmakefile
echo -e "\t@sh $basedir/generate-test-report.sh . $testlistfile" >> $testmakefile

# Generate clean target
echo "" >> $testmakefile
echo "clean:" >> $testmakefile
echo -e "\trm -f test-report.html" >> $testmakefile
echo -e "\trm -f test-report.html.top" >> $testmakefile
echo -e "\trm -f test-report.html.bottom" >> $testmakefile
echo -e "\trm -f test-report.html.entry" >> $testmakefile
echo -e "\trm -f test-report.html.summary" >> $testmakefile
echo -e "\trm -f test.*.*.files.html" >> $testmakefile
echo -e "\trm -f test.*.*.file.*" >> $testmakefile
echo -e "\trm -f test-report.json" >> $testmakefile
echo -e "\trm -f tmp.*" >> $testmakefile
