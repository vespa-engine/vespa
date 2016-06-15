#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

declare -r DUMMY_KERNEL_NETWORK_MODULE="dummy"
declare -r DUMMY_NETWORK_INTERFACE="dummy0"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command>
Manages the network bridge to the Docker container network

Commands:
  start     Set up the network bridge
  stop      Tear down the network bridge
  restart   Stop, then start
EOF
}

function Stop {
    echo -n "Removing bridge $HOST_BRIDGE_INTERFACE... "
    sudo ip link del "$HOST_BRIDGE_INTERFACE" &>/dev/null || true

    if sudo lsmod | grep -q "$DUMMY_KERNEL_NETWORK_MODULE"
    then
        sudo rmmod "$DUMMY_KERNEL_NETWORK_MODULE"
    fi

    echo done
}

function MakeBridge {
    local ip="$1"
    local prefix_bitlength="$2"
    local name="$3"

    if ip link show dev "$name" up &>/dev/null
    then
        # TODO: Verify it is indeed set up correctly.
        echo "Bridge '$name' already exists, will assume it has been set up correctly"
    else
        echo -n "Adding bridge $name ($ip) to the container network... "

        # Check if the $DUMMY_NETWORK_INTERFACE module is loaded and load if it is not
        if ! sudo ip link show $DUMMY_NETWORK_INTERFACE &> /dev/null; then
            sudo modprobe "$DUMMY_KERNEL_NETWORK_MODULE"
        fi
        sudo ip link set "$DUMMY_NETWORK_INTERFACE" up
        sudo ip link add dev "$name" link "$DUMMY_NETWORK_INTERFACE" type macvlan mode bridge
        sudo ip addr add dev "$name" "$ip/$prefix_bitlength" broadcast +
        sudo ip link set dev "$name" up
        echo done
    fi
}

function Start {
    MakeBridge "$HOST_BRIDGE_IP" "$NETWORK_PREFIX_BITLENGTH" "$HOST_BRIDGE_INTERFACE"
}

Main "$@"
