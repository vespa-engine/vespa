#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command> [<option>]...
Manage Hosted Vespa zone on localhost using Docker.

The Docker daemon must already be running, and the Docker image must have been
built. The node-admin module must have been packaged.

Commands:
  start     Start zone (start Config Server, Node Admin, etc)
  stop      Stop zone (take down Node Admin, Config Server, etc)
  restart   Stop, then start

Options:
  --hv-env <env>
            Make a zone with this Hosted Vespa environment. Must be one of
            prod, dev, test, staging, etc. Default is $DEFAULT_HOSTED_VESPA_ENVIRONMENT.
  --hv-region <region>
            Make a zone with this Hosted Vespa region. Default is $DEFAULT_HOSTED_VESPA_REGION.
  --num-nodes <num-nodes>
            Make a zone with <num-nodes> Docker nodes instead of the default $DEFAULT_NUM_APP_CONTAINERS.
EOF
}

function Stop {
    if (($# != 0))
    then
        Usage
    fi

    # Prime sudo to avoid password prompt in the middle of the script.
    sudo true

    ./node-admin.sh stop

    # TODO: Stop and remove existing vespa node containers.

    # There's no need to stop populate-noderepo-with-local-nodes.sh, as the
    # whole node repo is going down when the config server is stopped.
    #
    # ./populate-noderepo-with-local-nodes.sh stop

    ./config-server.sh stop
    ./make-host-like-container.sh stop
    ./network-bridge.sh stop
    ./etc-hosts.sh stop
}

function Start {
    if (($# != 0))
    then
        Usage
    fi

    # Prime sudo to avoid password prompt in the middle of the script.
    sudo true

    ./etc-hosts.sh --num-nodes "$NUM_APP_CONTAINERS"
    ./network-bridge.sh
    ./make-host-like-container.sh

    local region="${OPTION_HV_REGION:-$DEFAULT_HOSTED_VESPA_REGION}"
    local env="${OPTION_HV_ENV:-$DEFAULT_HOSTED_VESPA_ENVIRONMENT}"
    ./config-server.sh --wait=true --hv-region="$region" --hv-env="$env"

    ./populate-noderepo-with-local-nodes.sh --num-nodes "$NUM_APP_CONTAINERS"
    ./node-admin.sh
}

# Makes it easier to access scripts in the same 'scripts' directory
cd "$SCRIPT_DIR"

Main "$@"
