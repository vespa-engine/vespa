#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# WARNING: This script should be kept in sync with the file used by Chef:
#     vespa-cookbooks/hosted/files/default/prepost-instance.sh
# TODO: Remove the above cookbook file (with the down-side that a new script
# requires a new vespa release, instead of just a hosted release).

# Usage: nodectl-instance.sh [start|stop|suspend]
#
# start: Set the node "in service" by e.g. undraining container traffic.
# start can be assumed to have completed successfully.
#
# stop: Stop services on the node (Note: Only does suspend now, will be changed soon, Oct 24 2016)
#
# suspend: Prepare for a short suspension, e.g. there's a pending upgrade. Set the
# node "out of service" by draining container traffic, and flush index for a
# quick start after the suspension. There's no need to stop.

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

has_servicename() {
    local name="$1"
    $VESPA_HOME/bin/vespa-model-inspect host $(hostname) | grep -q "$name @ "
    return $?
}

has_container() {
    has_servicename container || has_servicename qrserver
}

has_searchnode() {
    has_servicename searchnode
}

container_drain() {
    # TODO: Implement proper draining
    sleep 60
}

Resume() {
    # Always start vip for now
    $echo $VESPA_HOME/bin/vespa-routing vip -u chef in
}

# TODO: Remove vespa-routing when callers have been updated to use resume
Start() {
    # Always start vip for now
    $echo $VESPA_HOME/bin/vespa-routing vip -u chef in
}

Stop() {
    yinst stop
}

Suspend() {
    # Always stop vip for now
    $echo $VESPA_HOME/bin/vespa-routing vip -u chef out

    if has_searchnode; then
        $echo $VESPA_HOME/bin/vespa-proton-cmd --local prepareRestart
    fi

    if has_container; then
        $echo container_drain
    fi
}

main() {
    if [ $# -lt 1 ]; then
        echo "Usage: $0 [-e] start|stop|suspend" >&2
        exit 1
    fi

    echo=""
    if [ "$1" = "-e" ]; then
        echo=echo
        shift
    fi

    action="$1"

    if [ "$action" = "start" ]; then
        Start
    elif [ "$action" = "stop" ]; then
        Stop
    elif [ "$action" = "suspend" ]; then
        Suspend
    elif [ "$action" = "resume" ]; then
        Resume
    else
        echo "Unknown action: $action" >&2
        exit 1
    fi
}

main "$@"
