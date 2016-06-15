#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

top_color="bgcolor=\"#ccccff\""
row_color="bgcolor=\"#eeeeff\""
pass_color="bgcolor=\"#ccffcc\""
fail_color="bgcolor=\"#ffcccc\""
ignore_color="bgcolor=\"#ffffcc\""

#
# Generate top of HTML report
# $1 - output file
#
gen_report_top() {
    dst=$1
    date=`date "+%Y-%m-%d %H:%M:%S"`
    echo "<html>" > $dst
    echo "<title>Test Report ($date)</title>" >> $dst
    echo "<body bgcolor=\"#ffffff\">" >> $dst
    echo "<h1>Test Report ($date)</h1>" >> $dst
}

#
# Generate top of JSON report
# $1 - output file
#
gen_report_top_json() {
    dst=$1
    echo "{" >> $dst
}

#
# Generate bottom of HTML report
# $1 - output file
#
gen_report_bottom() {
    dst=$1
    echo "</body>" > $dst
    echo "</html>" >> $dst
}

#
# Generate bottom of JSON report
# $1 - output file
#
gen_report_bottom_json() {
    dst=$1
    echo "}" >> $dst
}

#
# Generate top of test table
# $1 - output file
#
gen_report_entry_init() {
    dst=$1
    echo "<h2>Details</h2>" > $dst
    echo "<table cellspacing=\"2\" cellpadding=\"5\" border=\"0\">" >> $dst
    echo "<tr>" >> $dst
    echo "<th align=\"left\" $top_color>Test</th>" >> $dst
    echo "<th align=\"left\" $top_color>Result</th>" >> $dst
    echo "<th align=\"left\" $top_color>Time (s)</th>" >> $dst
    echo "</tr>" >> $dst
}

#
# Generate top of tests entry
# $1 - output file
#
gen_report_entry_init_json() {
    dst=$1
    echo "\"test_suites\": [" >> $dst
}

#
# Generate bottom of test table
# $1 - output file
#
gen_report_entry_fini() {
    dst=$1
    echo "</table>" >> $dst
}

#
# Generate end of tests entry
# $1 - output file
#
gen_report_entry_fini_json() {
    dst=$1
    echo "]," >> $dst
}

#
# Generate test report summary
# $1 - output file
# $test_cnt   - total number of tests
# $pass_cnt   - number of tests passed
# $fail_cnt   - number of tests failed
# $ignore_cnt - number of tests ignored
# $elapsed    - total time spent tesing
#
gen_report_summary() {
    dst=$1
    echo "<h2>Summary</h2>" > $dst
    echo "<table cellspacing=\"2\" cellpadding=\"5\" border=\"0\">" >> $dst
    echo "<tr>" >> $dst
    echo "<th align=\"left\" $top_color>Tests</th>" >> $dst
    echo "<th align=\"left\" $top_color>Pass</th>" >> $dst
    echo "<th align=\"left\" $top_color>Fail</th>" >> $dst
    echo "<th align=\"left\" $top_color>Ignore</th>" >> $dst
    echo "<th align=\"left\" $top_color>Time (s)</th>" >> $dst
    echo "</tr>" >> $dst
    echo "<tr>" >> $dst
    echo "<td align=\"right\" $row_color>$test_cnt</td>" >> $dst
    echo "<td align=\"right\" $row_color>$pass_cnt</td>" >> $dst
    echo "<td align=\"right\" $row_color>$fail_cnt</td>" >> $dst
    echo "<td align=\"right\" $row_color>$ignore_cnt</td>" >> $dst
    echo "<td align=\"right\" $row_color>$elapsed</td>" >> $dst
    echo "</tr>" >> $dst
    echo "</table>" >> $dst
}

gen_report_summary_json() {
    dst=$1
    echo "\"summary\": {" >> $dst
    echo "\"total\": $test_cnt," >> $dst
    echo "\"pass\": $pass_cnt," >> $dst
    echo "\"fail\": $fail_cnt," >> $dst
    echo "\"ignore\": $ignore_cnt," >> $dst
    echo "\"time\": $elapsed" >> $dst
    echo "}" >> $dst
}

progname=`basename $0 .sh`
if [ $# -lt 2 ]; then
    echo "usage: $0 <rundir> <testlist>"
    echo "  rundir: where to run and generate output"
    echo "  testlist: file with test names (relative to rundir)"
    exit 1
fi

rundir=$1
testlist=`cat $2` || exit 1

report=test-report.html
jsonreport=test-report.json
gen_report_top $report.top
gen_report_top_json $jsonreport
gen_report_entry_init $report.entry
gen_report_entry_init_json $jsonreport

test_cnt=0
fail_cnt=0
pass_cnt=0
ignore_cnt=0
for test in $testlist; do
    test_cnt=$(($test_cnt + 1))
    negate=false
    ignore=false
    case $test in
    \!*) negate=true; prefix="[!]"; test=${test#!};;
    \?*) ignore=true; prefix="[?]"; test=${test#?};;
    esac
    prefix=""
    test_name="${prefix}"`echo $test | sed -e "s=^tests/==;s=/=.=g"`
    test=`basename $test`
    test_result=test.$test_cnt.$test_name.result
    read result < $test_result
    if [ $result -eq 0 ]; then
    if $ignore; then
        ignore_cnt=$(($ignore_cnt + 1))
    else
        pass_cnt=$(($pass_cnt + 1))
    fi
    else
    if $ignore; then
        ignore_cnt=$(($ignore_cnt + 1))
    else
        fail_cnt=$(($fail_cnt + 1))
        cat test.$test_cnt.$test_name.log.file.txt
    fi
    fi
    # Unfortunately, the JSON format is a bit context sensitive, so we need to do the old comma-before-entry-except-first logic
    if [ $test_cnt -gt 1 ]; then
        echo "," >> $jsonreport
    fi
    test_report=tmp.report.$test_name.json
    cat $test_report >> $jsonreport
    rm -f $test_report
    rm -f $test_result
done

date +%s > tmp.end-time
elapsed=$((`cat tmp.end-time` - `cat tmp.start-time`))
gen_report_entry_fini $report.entry
gen_report_entry_fini_json $jsonreport
gen_report_summary $report.summary
gen_report_summary_json $jsonreport
gen_report_bottom $report.bottom
gen_report_bottom_json $jsonreport
cat $report.top $report.summary $report.entry $report.bottom > $report

echo "summary: test/pass/fail/ignore: $test_cnt/$pass_cnt/$fail_cnt/$ignore_cnt"
if [ $fail_cnt -eq 0 ]; then
    echo "SUCCESS"
    exit 0
else
    echo "FAILURE"
    exit 1
fi
