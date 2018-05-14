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

findroot

# END environment bootstrap section

export VESPA_LOG_TARGET=file:/dev/null
export MALLOC_ARENA_MAX=1 # Does not need fast allocation
java \
-server -enableassertions \
-XX:ThreadStackSize=512 \
-XX:MaxJavaStackTraceDepth=-1 \
-Djava.library.path=${VESPA_HOME}/libexec64/native:${VESPA_HOME}/lib64 \
-XX:MaxDirectMemorySize=32m -Djava.awt.headless=true \
-Xms128m -Xmx1024m $(getJavaOptionsIPV46) \
-cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.vespafeeder.VespaFeeder "$@"
