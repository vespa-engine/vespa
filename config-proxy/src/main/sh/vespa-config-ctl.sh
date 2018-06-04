#!/bin/bash
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

# END environment bootstrap section

ROOT=${VESPA_HOME%/}
export ROOT
cd $ROOT || { echo "Cannot cd to $ROOT" 1>&2; exit 1; }

P_SENTINEL=var/run/sentinel.pid
P_CONFIG_PROXY=var/run/configproxy.pid

export P_SENTINEL P_CONFIG_PROXY

LOGDIR="$ROOT/logs/vespa"
LOGFILE="$LOGDIR/vespa.log"
VESPA_LOG_TARGET="file:$LOGFILE"
VESPA_LOG_CONTROL_DIR="$ROOT/var/db/vespa/logcontrol"
cp="libexec/vespa/patches/configproxy:lib/jars/config-proxy-jar-with-dependencies.jar"

VESPA_LOG_LEVEL="all -debug -spam"

export VESPA_LOG_TARGET VESPA_LOG_LEVEL VESPA_LOG_CONTROL_DIR
export VESPA_SENTINEL_PORT

mkdir -p "$LOGDIR"
mkdir -p "$VESPA_LOG_CONTROL_DIR"

hname=$(vespa-print-default hostname)

CONFIG_ID="hosts/$hname"
export CONFIG_ID
export MALLOC_ARENA_MAX=1 #Does not need fast allocation
export LD_LIBRARY_PATH="$VESPA_HOME/lib64"


case $1 in
    start)
        configsources=`bin/vespa-print-default configservers_rpc`
        userargs=$vespa_base__jvmargs_configproxy
        if [ "$userargs" == "" ]; then
            userargs=$services__jvmargs_configproxy
        fi
        jvmopts="-Xms32M -Xmx256M -XX:ThreadStackSize=256 -XX:MaxJavaStackTraceDepth=-1"

        VESPA_SERVICE_NAME=configproxy
        export VESPA_SERVICE_NAME
        echo "Starting config proxy using $configsources as config source(s)"
        vespa-runserver -r 10 -s configproxy -p $P_CONFIG_PROXY -- \
            java ${jvmopts} \
                 -XX:OnOutOfMemoryError="kill -9 %p" $(getJavaOptionsIPV46) \
                 -Dproxyconfigsources="${configsources}" ${userargs} \
                 -cp $cp com.yahoo.vespa.config.proxy.ProxyServer 19090

        echo "Waiting for config proxy to start"
        fail=true
        for ((sleepcount=0;$sleepcount<600;sleepcount=$sleepcount+1)) ; do
            sleep 0.1
            if [ -f $P_CONFIG_PROXY ] && kill -0 `cat $P_CONFIG_PROXY` && vespa-ping-configproxy -s $hname 2>/dev/null
            then
                echo "config proxy started (runserver pid `cat $P_CONFIG_PROXY`)"
                fail=false
                break
            fi
        done
        if $fail ; then
            echo "Failed to start config proxy!" 1>&2
            echo "look for reason in vespa.log, last part follows..."
            tail -n 15 $LOGFILE | vespa-logfmt -
            exit 1
        fi

        VESPA_SERVICE_NAME=config-sentinel
        export VESPA_SERVICE_NAME

        vespa-runserver -s config-sentinel -r 10 -p $P_SENTINEL -- \
            sbin/vespa-config-sentinel -c "$CONFIG_ID"
        ;;

    stop)
        vespa-runserver -s config-sentinel -p $P_SENTINEL -S
        vespa-runserver -s configproxy     -p $P_CONFIG_PROXY -S
        ;;

    *)
        echo "Unknown option $@" 1>&2
        exit 1
        ;;
esac
