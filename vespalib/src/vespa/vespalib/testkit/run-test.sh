#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

top_color="bgcolor=\"#ccccff\""
row_color="bgcolor=\"#eeeeff\""
pass_color="bgcolor=\"#ccffcc\""
fail_color="bgcolor=\"#ffcccc\""
ignore_color="bgcolor=\"#ffffcc\""


#
# Generate a single test table entry
# $1 - output file
# $test_name   - test name
# $test_log    - href to test log
# $test_desc   - href to test description (or 'false')
# $test_files  - href to test files (or 'false')
# $test_time   - timing info for test run
#
# $result - test outcome (0 means pass)
# $ignore - ignore current test (true/false)
#
gen_report_entry() {
    dst=$1
    echo "<tr>" >> $dst
    echo -n "<td $row_color>$test_name (" >> $dst
    if [ $test_desc != "false" ]; then
	echo -n "<a href=\"$test_desc\">desc</a> " >> $dst
    fi
    echo -n "<a href=\"$test_log\">log</a>" >> $dst
    if [ $test_files != "false" ]; then
	echo -n " <a href=\"$test_files\">files</a>" >> $dst
    fi
    echo -n ")</td>" >> $dst
    if [ $result -eq 0 ]; then
	if $ignore; then
	    echo "<td $ignore_color>PASS</td>" >> $dst
	else
	    echo "<td $pass_color>PASS</td>" >> $dst
	fi
    else
	if $ignore; then
	    echo "<td $ignore_color>FAIL</td>" >> $dst
	else
	    echo "<td $fail_color>FAIL</td>" >> $dst
	fi
    fi
    echo "<td $row_color>$test_time</td>" >> $dst
    echo "</tr>" >> $dst
}

#
# Generate a single test entry in JSON
# Arguments same as for gen_report_entry()
#
gen_report_entry_json() {
    dst=$1
    echo "{" >> $dst
    echo "\"name\": \"$test_name\"," >> $dst
    local log=`cat $test_log | base64 -w 0`
    echo "\"log\": \"$log\"," >> $dst
    echo "\"ignored\": $ignore," >> $dst
    echo "\"time\": $test_run_time," >> $dst
    local success="true"
    if [ $result == 0 ]; then
        success="true"
    else
        success="false"
    fi
    echo "\"success\": $success" >> $dst
    echo -n "}" >> $dst
}

get_file_size () {
	if [ `uname` = FreeBSD ]; then
	    stat -f "%z" "$1"
	    return 0
	fi
	if [ `uname` = Linux ]; then
	    stat -c "%s" "$1"
	    return 0
	fi
	ls -l "$1" | awk '{print $5}'
}

#
# Generate test file list
# $1 - output file
# $test_name - test name
# $test_path - path to test
# $test_cnt  - total number of tests (so far)
#
gen_file_list() {
    dst=$1
    echo "<html>" > $dst
    echo "<title>File List for $test_name</title>" >> $dst
    echo "<body bgcolor=\"#ffffff\">" >> $dst
    echo "<h1>File List for $test_name</h1>" >> $dst
    echo "<table cellspacing=\"2\" cellpadding=\"5\" border=\"0\">" >> $dst
    echo "<tr>" >> $dst
    echo "<th align=\"left\" $top_color>Filename</th>" >> $dst
    echo "<th align=\"left\" $top_color>Size (bytes)</th>" >> $dst
    echo "</tr>" >> $dst
    filelist=`cat $test_path/FILES`
    for file in $filelist; do
	file_link=test.$test_cnt.$test_name.file.$file.txt
	file_link=`echo "$file_link" | sed -e "s=/=.=g"`
	echo "<tr>" >> $dst
	if [ -f $test_path/$file ]; then
	    cp $test_path/$file $file_link
	    file_size=`get_file_size $file_link`
	    echo "<td $row_color><a href=\"$file_link\">$file</a></td>" >> $dst
	    echo "<td align=\"right\" $row_color>$file_size</td>" >> $dst
	else
	    echo "<td $row_color>$file</td>" >> $dst
	    echo "<td align=\"right\" $row_color>not found</td>" >> $dst
	fi
	echo "</tr>" >> $dst
    done
    echo "</table>" >> $dst
    echo "</body>" >> $dst
    echo "</html>" >> $dst
}

#
# Run one test
#
run_one_test() {
    test=$1
    test_cnt=$2
    test_name=$3
    test_path=$4
    test_log=$5
    test_result=$6
    negate=$7
    ignore=$8

    rm -f tmp.$test_name.log-control
    export VESPA_LOG_CONTROL_FILE=`pwd`/tmp.$test_name.log-control
    # run test
    local starttime=`date +%s`
    /usr/bin/time -o tmp.${test}-time sh -c \
	"(cd $test_path && $MAKE -s test $makeargs) > $test_log 2>&1"
    result=$?
    local endtime=`date +%s`
    test_run_time=$(($endtime - $starttime))

    #If you have run with valgrind check errors
   if [ $result -eq 0 ]; then
       valgrind_errors=`grep "ERROR SUMMARY" $test_log | cut -d ' ' -f4`
       for r in $valgrind_errors
       do
           result=$(($result + $r))
       done
   fi

    # handle test description
    if [ -f $test_path/DESC ]; then
	test_desc=test.$test_cnt.$test_name.desc.file.txt
	cp $test_path/DESC $test_desc
    else
	test_desc="false"
    fi

    # handle test file list
    if [ -f $test_path/FILES ]; then
	test_files=test.$test_cnt.$test_name.files.html
	gen_file_list $test_files
    else
	test_files="false"
    fi

    # handle test result negation
    if $negate; then
	if [ $result -eq 0 ]; then
	    result=1
	else
	    result=0
	fi
    fi
    result_string=""
    if [ $result -eq 0 ]; then
	if $ignore; then
	    result_string="PASS (ignored)"
	else
	    result_string="PASS"
	fi
    else
	if $ignore; then
	    result_string="FAIL (ignored)"
	else
	    result_string="FAIL"
	fi
    fi

    test_time=`cat tmp.${test}-time`
    echo $test_name : $result_string
    echo $result > $test_result
    gen_report_entry $report.entry
    gen_report_entry_json tmp.report.$test_name.json
}

progname=`basename $0 .sh`
if [ $# -lt 4 ]; then
	echo "usage: $0 <rundir> <test> <testid> <top>"
	echo "  rundir: where to run and generate output"
	echo "  test: content of testdir"
    echo"   testid: unique id for this test (when run in parallel with other tests)"
	echo "  top: path to top (relative to rundir)"
	exit 1
fi

mkdir -p $1
cd $1 || exit 1
test=$2
test_cnt=$3
top_path=$4

: ${MAKE:=gmake}
unset MAKELEVEL

report=test-report.html
jsonreport=test-report.json

negate=false
ignore=false
case $test in
\!*) negate=true; prefix="[!]"; test=${test#!};;
\?*) ignore=true; prefix="[?]"; test=${test#?};;
esac
prefix=""
test_name="${prefix}"`echo $test | sed -e "s=^tests/==;s=/=.=g"`
test_path=$top_path/$test
test=`basename $test`
test_log=test.$test_cnt.$test_name.log.file.txt
test_result=test.$test_cnt.$test_name.result
# run test
echo "running test '$test_name' ... "

run_one_test $test $test_cnt $test_name $test_path $test_log $test_result $negate $ignore
