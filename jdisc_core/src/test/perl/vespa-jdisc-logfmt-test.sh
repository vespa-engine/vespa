#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
MYPATH=`dirname ${0}`
DIFF=/usr/bin/diff
LOGFMT=${1}

if which perl &> /dev/null; then
    echo "Running vespa-jdisc-logfmt test suite."
else
    echo "Ignoring vespa-jdisc-logfmt test suite as there is no perl executable."
    exit 0
fi

set -e
export TZ=CET
export VESPA_HOME=$(mktemp -d /tmp/mockup-vespahome-XXXXXX)/
mkdir -p $VESPA_HOME/libexec/vespa
touch $VESPA_HOME/libexec/vespa/common-env.sh

echo

${LOGFMT} -h                                  2>&1 | ${DIFF} - ${MYPATH}/help.expected
${LOGFMT} -h -L event                         2>&1 | ${DIFF} - ${MYPATH}/help.Levent.expected

${LOGFMT} ${MYPATH}/jdisc.log                 2>&1 | ${DIFF} - ${MYPATH}/jdisc.expected
${LOGFMT} -l all ${MYPATH}/jdisc.log          2>&1 | ${DIFF} - ${MYPATH}/jdisc.lall.expected
${LOGFMT} -l all,-info ${MYPATH}/jdisc.log    2>&1 | ${DIFF} - ${MYPATH}/jdisc.lall_info.expected
${LOGFMT} -s +pid ${MYPATH}/jdisc.log         2>&1 | ${DIFF} - ${MYPATH}/jdisc.spid.expected

${LOGFMT} ${MYPATH}/vespa.log                 2>&1 | ${DIFF} - ${MYPATH}/vespa.expected
${LOGFMT} -L event ${MYPATH}/vespa.log        2>&1 | ${DIFF} - ${MYPATH}/vespa.Levent.expected
${LOGFMT} -L event -l all ${MYPATH}/vespa.log 2>&1 | ${DIFF} - ${MYPATH}/vespa.Levent.lall.expected

rm -r ${VESPA_HOME}
echo All tests passed.
