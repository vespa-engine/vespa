#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        # ensure it ends with "/" :
        VESPA_HOME=${VESPA_HOME%/}/
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

[ "$VESPA_HOME" ] || { echo "Missing VESPA_HOME variable" 1>&2; exit 1; }

cd $VESPA_HOME || { echo "Cannot cd to $VESPA_HOME" 1>&2; exit 1; }

fixdir () {
    if [ $# != 4 ]; then
        echo "fixdir: Expected 4 params, got:" "$@"
        exit 1
    fi
    mkdir -p "$4"
    chown $1 "$4"
    chgrp $2 "$4"
    chmod $3 "$4"
}

# BEGIN directory fixups

fixdir yahoo wheel  755  libdata/yell/exception
fixdir yahoo wheel  775  libexec/vespa/modelplugins
fixdir yahoo wheel  755  libexec/vespa/plugins/qrs
fixdir root  wheel 1777  logs
fixdir yahoo wheel 1777  logs/vespa
fixdir yahoo wheel  755  logs/vespa/qrs
fixdir yahoo wheel  755  logs/vespa/search
fixdir root  wheel 1777  tmp
fixdir yahoo wheel 1777  tmp/vespa
fixdir yahoo wheel  755  var/cache/vespa/config
fixdir yahoo wheel 1777  var/crash
fixdir yahoo wheel  755  var/db/vespa
fixdir yahoo wheel  755  var/db/vespa/config_server
fixdir yahoo wheel  755  var/db/vespa/config_server/serverdb
fixdir yahoo wheel  755  var/db/vespa/config_server/serverdb/applications
fixdir yahoo wheel  755  var/db/vespa/config_server/serverdb/configs
fixdir yahoo wheel  755  var/db/vespa/config_server/serverdb/configs/application
fixdir yahoo wheel  755  var/db/vespa/index
fixdir yahoo wheel  755  var/db/vespa/search
fixdir yahoo wheel  755  var/db/vespa/logcontrol
fixdir root  wheel 1777  var/run

chown -hR yahoo logs/vespa
chown -hR yahoo var/db/vespa

# END directory fixups

# Delete temporary files created by storage when running.
rm -f /home/y/tmp/hostinfo.*.*.report

# Add $VESPA_HOME/bin to default path
perl -pi -e 'm=^pathmunge /usr/X11R6/bin after= and s=^=pathmunge /home/y/bin after; =' /etc/profile

#Enable core files by default
perl -pi -e 's/^# No core files by default/# Vespa: Enable core files by default/' /etc/profile
perl -pi -e 's/^ulimit -S -c 0/ulimit -S -c unlimited/' /etc/profile

# Don't fail script if this command fails.
#  * sysctl will always return error on openvz jails
sysctl kernel.core_pattern="|/home/y/bin/vespa-core-dumper /home/y/bin64/lz4 /home/y/var/crash/%e.core.%p.lz4" || true
