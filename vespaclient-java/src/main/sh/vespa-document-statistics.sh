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

function help {
    echo "Usage: vespa-document-statistics [ category, ... ]"
    echo "  Where category is one or more of: user, group, scheme, namespace"
    echo ""
    echo "vespa-document-statistics generates documents counts based on one or more categories."
    exit 0
}
if [ "$1" == "-h" ]; then
  help
fi
if [ "$1" == "" ]; then
  help
fi
export MALLOC_ARENA_MAX=1 #Does not need fast allocation
exec java -Xms32m -Xmx128m $(getJavaOptionsIPV46) -cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.vespavisit.Main --statistics "$1"
