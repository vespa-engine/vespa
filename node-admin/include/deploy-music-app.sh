#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Usage: docker exec config-server /usr/local/bin/deploy-music-app.sh
# Deploy app to config-server running in local Docker zone
#
# You must build the vespa-local:latest (vespa/vespa/node-admin) image and
# (re)start the local zone, before running deploy-music-app.sh.
#
# See also app.sh

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

set -e
set -x

declare -r CONFIG_SERVER_HOSTNAME=config-server
declare -r CONFIG_SERVER_PORT=19071
declare -r TENANT_NAME=localtenant
# TODO: Make it possible to deploy any app from host context, not only this app (from within container).
declare -r VESPA_APP=$VESPA_HOME/share/vespa/sampleapps/search/music

# Create tenant
curl -X PUT $CONFIG_SERVER_HOSTNAME:$CONFIG_SERVER_PORT/application/v2/tenant/$TENANT_NAME

# Deploy sample app
deploy -e "$TENANT_NAME" prepare $VESPA_APP
deploy -e "$TENANT_NAME" activate
