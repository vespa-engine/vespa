#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

declare CONTAINER_ROOT_DIR="$APPLICATION_STORAGE_ROOT/$CONFIG_SERVER_CONTAINER_NAME"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command> [--wait]
Manage the Config Server

Commands:
  start     Start the Config Server in a Docker container
  stop      Remove the Config Server container
  restart   Stop, then start

Options:
  --hv-env <env>
            Start the config server with the given hosted Vespa environment
            name. Must be one of prod, dev, test, staging, etc. Default is
            $DEFAULT_HOSTED_VESPA_ENVIRONMENT.
  --hv-region <region>
            Start the config server with the given hosted Vespa region name.
            Default is $DEFAULT_HOSTED_VESPA_REGION.
  --wait true
            Make start wait until the Config Server is healthy
EOF
}

function Stop {
    # Prime sudo
    sudo true

    echo -n "Removing $CONFIG_SERVER_CONTAINER_NAME container... "
    docker rm -f "$CONFIG_SERVER_CONTAINER_NAME" &>/dev/null || true
    echo done

    if [ -d "$CONTAINER_ROOT_DIR" ]
    then
        # Double-check we're not 'rm -rf' something unexpected!
        if ! [[ "$CONTAINER_ROOT_DIR" =~ ^/home/docker/container-storage/ ]]
        then
            Fail "DANGEROUS: Almost removed '$CONTAINER_ROOT_DIR'..."
        fi

        echo -n "Removing container dir $CONTAINER_ROOT_DIR... "
        sudo rm -rf "$CONTAINER_ROOT_DIR"
        # The next two statements will prune empty parent directories.
        sudo mkdir "$CONTAINER_ROOT_DIR"
        sudo rmdir --ignore-fail-on-non-empty -p "$CONTAINER_ROOT_DIR"
        echo done
    fi
}

function Start {
    # Prime sudo
    sudo true

    local wait="${OPTION_WAIT:-true}"
    case "$wait" in
        true|false) : ;;
        *) Usage "--wait should only be set to true or false" ;;
    esac

    local region="${OPTION_HV_REGION:-$DEFAULT_HOSTED_VESPA_REGION}"
    local environment="${OPTION_HV_ENV:-$DEFAULT_HOSTED_VESPA_ENVIRONMENT}"

    echo -n "Creating container dir $CONTAINER_ROOT_DIR... "
    local shared_dir_on_localhost="$APPLICATION_STORAGE_ROOT/$CONFIG_SERVER_CONTAINER_NAME/$ROOT_DIR_SHARED_WITH_HOST"
    sudo mkdir -p "$shared_dir_on_localhost"
    sudo chmod a+wt "$shared_dir_on_localhost"
    echo done

    # Start config server
    echo -n "Making $CONFIG_SERVER_CONTAINER_NAME container... "
    local config_server_container_id
    config_server_container_id=$(\
        docker run \
               --detach \
               --cap-add=NET_ADMIN \
               --net=none \
               --hostname "$CONFIG_SERVER_HOSTNAME" \
               --name "$CONFIG_SERVER_CONTAINER_NAME" \
               --volume "/etc/hosts:/etc/hosts" \
               --volume "$shared_dir_on_localhost:/$ROOT_DIR_SHARED_WITH_HOST" \
               --env "HOSTED_VESPA_REGION=$region" \
               --env "HOSTED_VESPA_ENVIRONMENT=$environment" \
               --env "CONFIG_SERVER_HOSTNAME=$CONFIG_SERVER_HOSTNAME" \
               --env "HOST_BRIDGE_IP=$HOST_BRIDGE_IP" \
               --entrypoint /usr/local/bin/start-config-server.sh \
               "$DOCKER_IMAGE")
    echo done

    echo -n "Verifying that $CONFIG_SERVER_CONTAINER_NAME container is running... "
    local config_server_container_pid
    config_server_container_pid=$(docker inspect -f '{{.State.Pid}}' "$CONFIG_SERVER_CONTAINER_NAME")

    echo -n "(pid $config_server_container_pid) "

    # TODO: Use .State.Status instead (only supported from version 1.9).
    local config_server_container_running
    config_server_container_running=$(docker inspect -f '{{.State.Running}}' "$CONFIG_SERVER_CONTAINER_NAME")

    if [ "$config_server_container_pid" == 0 -o "$config_server_container_running" != true ]
    then
        echo "failed"
        Fail "The Config Server is not running anymore, consider looking" \
             "at the logs with 'docker logs $CONFIG_SERVER_CONTAINER_NAME'"
    fi
    echo "done"

    echo -n "Setting up the $CONFIG_SERVER_CONTAINER_NAME container network of type $NETWORK_TYPE... "
    if ! script_out=$(sudo ./configure-container-networking.py --"$NETWORK_TYPE" "$config_server_container_pid" "$CONFIG_SERVER_IP" 2>&1); then
        echo "failed"
        echo "$script_out"
        exit
    fi
    echo "done"

    if [ "$wait" == true ]
    then
        # Wait for config server to come up
        echo -n "Waiting for healthy Config Server (~30s)"
        local url="http://$CONFIG_SERVER_HOSTNAME:19071/state/v1/health"
        while ! curl --silent --fail --max-time 1 "$url" >/dev/null
        do
            echo -n .
            sleep 2
        done
        echo " done"
    fi
}

# Makes it easier to access scripts in the same 'scripts' directory
cd "$SCRIPT_DIR"

Main "$@"
