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

cd $VESPA_HOME || { echo "Cannot cd to $VESPA_HOME" 1>&2; exit 1; }

uid_name () {
    name=$(id -un -- $1)
    if [ $? -ne 0 ]; then
        echo "uid: $1"
        return
    fi
    echo $name
}

gid_name () {
    name=$(getent group $1)
    if [ $? -ne 0]; then
        echo "gid: $1"
    fi
    echo $name
}

fixdir () {
    if [ $# != 4 ]; then
        echo "fixdir: Expected 4 params, got:" "$@"
        exit 1
    fi
    mkdir -p "$4"
    if [ "$(id -u)" -ne 0 ]; then
        local owner_expected="$(id -u $1)"
        local owner_actual="$(stat -c %u $4)"
        if [ "$owner_expected" -ne "$owner_actual" ]; then
            echo "Wrong directory owner for /opt/vespa/$4, expected $(uid_name $owner_expected), was $(uid_name $owner_actual)"
            exit 1
        fi
        local group_expected="$(getent group $2 | cut -d: -f3)"
        local group_actual="$(stat -c %g $4)"
        if [ "$group_expected" -ne "$group_actual" ]; then
            echo "Wrong directory group for /opt/vespa/$4, expected $(gid_name $group_expected), was $(gid_name $group_actual)"
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
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  tmp
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  tmp/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/crash
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
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/application
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/bundlecache
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/bundlecache/configserver
fixdir ${VESPA_USER} ${VESPA_GROUP}   755  var/vespa/cache/config

if [ "$(id -u)" -eq 0 ]; then
    chown -hR ${VESPA_USER} logs/vespa
    chown -hR ${VESPA_USER} var/db/vespa
fi

# END directory fixups

# Delete temporary files created by storage when running.
rm -f ${VESPA_HOME}/tmp/hostinfo.*.*.report
