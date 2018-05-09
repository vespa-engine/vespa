#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

findpath () {
    myname=${0}
    mypath=${myname%/*}
    myname=${myname##*/}
    if [ "$mypath" ] && [ -d "$mypath" ]; then
        return
    fi
    mypath=$(pwd)
    if [ -f "${mypath}/${myname}" ]; then
        return
    fi
    echo "FATAL: Could not figure out the path where $myname lives from $0"
    exit 1
}

COMMON_ENV=libexec/vespa/common-env.sh

source_common_env () {
    if [ "$VESPA_HOME" ] && [ -d "$VESPA_HOME" ]; then
        export VESPA_HOME
        common_env=$VESPA_HOME/$COMMON_ENV
        if [ -f "$common_env" ]; then
            . $common_env
            return
        fi
    fi
    return 1
}

findroot () {
    source_common_env && return
    if [ "$VESPA_HOME" ]; then
        echo "FATAL: bad VESPA_HOME value '$VESPA_HOME'"
        exit 1
    fi
    if [ "$ROOT" ] && [ -d "$ROOT" ]; then
        VESPA_HOME="$ROOT"
        source_common_env && return
    fi
    findpath
    while [ "$mypath" ]; do
        VESPA_HOME=${mypath}
        source_common_env && return
        mypath=${mypath%/*}
    done
    echo "FATAL: missing VESPA_HOME environment variable"
    echo "Could not locate $COMMON_ENV anywhere"
    exit 1
}

findhost () {
    if [ "${VESPA_HOSTNAME}" = "" ]; then
        VESPA_HOSTNAME=$(vespa-detect-hostname) || exit 1
    fi
    vespa-validate-hostname "${VESPA_HOSTNAME}" || exit 1
    export VESPA_HOSTNAME
}

findroot
findhost

# END environment bootstrap section

ROOT=${VESPA_HOME%/}

printf "WARNING: vespa-activate-application is deprecated, use 'vespa-deploy activate' instead\n\n"

if [ "-f" == "$1" ] ; then
    $ROOT/bin/vespa-deploy activate
else
    STATUS=$($ROOT/bin/vespa-status-filedistribution)
    if [ $? -eq 0 ] ; then
       $ROOT/bin/vespa-deploy activate
    else
       echo "$STATUS"
       echo
       echo "Files are currently being distributed."
       echo "If you want to see the status, call 'vespa-status-filedistribution'."
       echo "Otherwise, call 'vespa-activate-application -f' to activate the application now; the file transfers will continue in the background."
       exit 1
    fi
fi
