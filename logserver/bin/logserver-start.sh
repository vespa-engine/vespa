#!/bin/sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

findpath () {
    myname=${0}
    mypath=${myname%/*}
    myname=${myname##*/}
    empty_if_start_slash=${mypath%%/*}
    if [ "${empty_if_start_slash}" ]; then
        mypath=$(pwd)/${mypath}
    fi
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
        VESPA_HOSTNAME=$(vespa-detect-hostname || hostname -f || hostname || echo "localhost") || exit 1
    fi
    validate="${VESPA_HOME}/bin/vespa-validate-hostname"
    if [ -f "$validate" ]; then
        "$validate" "${VESPA_HOSTNAME}" || exit 1
    fi
    export VESPA_HOSTNAME
}

findroot
findhost

ROOT=${VESPA_HOME%/}
export ROOT

# END environment bootstrap section

cd ${VESPA_HOME} || { echo "Cannot cd to ${VESPA_HOME}" 1>&2; exit 1; }

heap_min=32
heap_max=256
addopts="-server -Xms${heap_min}m -Xmx${heap_max}m -XX:+PreserveFramePointer $(get_jvm_hugepage_settings $heap_max) -XX:CompressedClassSpaceSize=32m -XX:MaxDirectMemorySize=32m -XX:ThreadStackSize=448 -XX:MaxJavaStackTraceDepth=1000 -XX:ActiveProcessorCount=2 -XX:-OmitStackTraceInFastThrow -Djava.io.tmpdir=${VESPA_HOME}/var/tmp"

oomopt="-XX:+ExitOnOutOfMemoryError"

jar="-jar ${VESPA_HOME}/lib/jars/logserver-jar-with-dependencies.jar"

export MALLOC_ARENA_MAX=1 #Does not need fast allocation

exec java $addopts "$oomopt" "$@" $jar
