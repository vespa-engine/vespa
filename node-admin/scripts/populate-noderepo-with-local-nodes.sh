#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

# Used to return response from RunCurl
declare CURL_RESPONSE

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command> [--num-nodes <num-nodes>]
Add Docker containers as nodes in the node repo, and activate them

Commands:
  start     Add and activate nodes
  stop      Remove nodes (not implemented)
  restart   Stop, then start

Options:
  --num-nodes <num-nodes>
            Activate <num-nodes> instead of the default $DEFAULT_NUM_APP_CONTAINERS.
EOF
}

function Stop {
    # TODO: Implement removal of the Docker containers from the node repo
    :
}

function Start {
    local -a hostnames=()

    local -i i=1
    for ((; i <= $NUM_APP_CONTAINERS; ++i)); do
        hostnames+=("$APP_HOSTNAME_PREFIX$i")
    done
    
    ./node-repo.sh add -c "$CONFIG_SERVER_HOSTNAME" -p "$HOSTNAME" \
                   "${hostnames[@]}"
}

Main "$@"
