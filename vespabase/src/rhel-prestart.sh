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

[ "$VESPA_HOME" ] || { echo "Missing VESPA_HOME variable" 1>&2; exit 1; }
if [ "$VESPA_USER" = "" ]; then
    VESPA_USER=$(id -run)
else
    if [ "$VESPA_GROUP" = "" ]; then
	VESPA_GROUP=$(id -gn $VESPA_USER)
    fi
fi
if [ "$VESPA_GROUP" = "" ]; then
    VESPA_GROUP=$(id -rgn)
fi
IS_ROOT=$([ "$(id -ru)" == "0" ] && echo true || echo false)

cd ${VESPA_HOME} || { echo "Cannot cd to ${VESPA_HOME}" 1>&2; exit 1; }

fixdir () {
    if [ $# != 4 ]; then
        echo "fixdir: Expected 4 params, got:" "$@"
        exit 1
    fi
    mkdir -p "$4"
    if ! $IS_ROOT; then
        local stat="$(stat -c "%U %G" $4)"
        local user=${stat% *}
        local group=${stat#* }
        if [ "$1" != "$user" ]; then
            echo "Wrong owner for ${VESPA_HOME}/$4, expected $1, was $user"
            exit 1
        fi
        if [ "$2" != "$group" ]; then
            echo "Wrong group for ${VESPA_HOME}/$4, expected $2, was $group"
            exit 1
        fi
    else
        chown $1 "$4"
        chgrp $2 "$4"
    fi
    chmod $3 "$4"
}

# BEGIN directory fixups

fixdir ${VESPA_USER} ${VESPA_GROUP}   755  logs
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  logs/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  logs/vespa/access
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  logs/vespa/configserver
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  logs/vespa/search
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/crash
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/config_server
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/config_server/serverdb
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/config_server/serverdb/tenants
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/download
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/filedistribution
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/index
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/logcontrol
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/search
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/db/vespa/tmp
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/jdisc_container
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/run
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/tmp
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/tmp/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/application
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/bundlecache
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/bundlecache/configserver
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/cache
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/cache/config

if $IS_ROOT; then
    chown -hR ${VESPA_USER} logs/vespa
    chown -hR ${VESPA_USER} var/db/vespa
fi

# END directory fixups

# Delete temporary files created by storage when running.
rm -f ${VESPA_HOME}/tmp/hostinfo.*.*.report
