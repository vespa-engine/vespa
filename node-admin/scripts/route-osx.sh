#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common-vm.sh"

VESPA_DOCKER_MACHINE_IP=$(docker-machine ip "$DOCKER_VM_NAME")
if [ $? -ne 0 ]; then
    echo "Could not get the IP of the docker-machine $DOCKER_VM_NAME"
    exit 1
fi

# Setup the route
sudo route delete "$HOST_BRIDGE_NETWORK" &> /dev/null
sudo route add "$HOST_BRIDGE_NETWORK" "$VESPA_DOCKER_MACHINE_IP"
