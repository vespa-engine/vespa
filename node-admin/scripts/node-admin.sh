#!/bin/bash
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

ROOT=$VESPA_HOME

set -e

source "${0%/*}/common.sh"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command>
Manage the Node Admin

Commands:
  start     Start the Node Admin in a Docker container
  stop      Remove the Node Admin container
  restart   Stop, then start
EOF
}

function Stop {
    # Prime sudo to avoid password prompt in the middle of the script.
    sudo true

    echo -n "Removing $NODE_ADMIN_CONTAINER_NAME container... "
    docker rm -f "$NODE_ADMIN_CONTAINER_NAME" &>/dev/null || true
    echo done
}

function Start {
    # Prime sudo to avoid password prompt in the middle of the script.
    sudo true

    echo -n "Making directory $APPLICATION_STORAGE_ROOT... "
    sudo mkdir -p $APPLICATION_STORAGE_ROOT
    echo done

    # Start node-admin
    echo -n "Making $NODE_ADMIN_CONTAINER_NAME container... "
    docker run \
           --detach \
           --privileged \
           --cap-add ALL \
           --name "$NODE_ADMIN_CONTAINER_NAME" \
           --net=host \
           --volume "$CONTAINER_CERT_PATH:/host/docker/certs" \
           --volume "/proc:/host/proc" \
           --volume "$APPLICATION_STORAGE_ROOT:/host$APPLICATION_STORAGE_ROOT" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/logs:$VESPA_HOME/logs" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/cache:$VESPA_HOME/var/cache" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/crash:$VESPA_HOME/var/crash" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/db/jdisc:$VESPA_HOME/var/db/jdisc" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/db/vespa:$VESPA_HOME/var/db/vespa" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/jdisc_container:$VESPA_HOME/var/jdisc_container" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/jdisc_core:$VESPA_HOME/var/jdisc_core" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/logstash-forwarder:$VESPA_HOME/var/logstash-forwarder" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/maven:$VESPA_HOME/var/maven" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/run:$VESPA_HOME/var/run" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/scoreboards:$VESPA_HOME/var/scoreboards" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/service:$VESPA_HOME/var/service" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/share:$VESPA_HOME/var/share" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/spool:$VESPA_HOME/var/spool" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/vespa:$VESPA_HOME/var/vespa" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/yca:$VESPA_HOME/var/yca" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/ycore++:$VESPA_HOME/var/ycore++" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/ymon:$VESPA_HOME/var/ymon" \
           --volume "/home/docker/container-storage/node-admin$VESPA_HOME/var/zookeeper:$VESPA_HOME/var/zookeeper" \
           --env "CONFIG_SERVER_ADDRESS=$CONFIG_SERVER_HOSTNAME" \
           --env "NETWORK_TYPE=$NETWORK_TYPE" \
           --entrypoint=/usr/local/bin/start-node-admin.sh \
           "$DOCKER_IMAGE" >/dev/null
    echo done
}

Main "$@"
