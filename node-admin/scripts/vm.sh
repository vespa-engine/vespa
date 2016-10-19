#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

source "${0%/*}/common-vm.sh"

DOCKER_VM_WAS_STARTED=false

if ! docker-machine status "$DOCKER_VM_NAME" &> /dev/null; then
  # Machine does not exist and we have to create and start
  docker-machine create -d virtualbox \
              --virtualbox-disk-size "$DOCKER_VM_DISK_SIZE_IN_MB" \
              --virtualbox-memory "$DOCKER_VM_MEMORY_SIZE_IN_MB" \
              --virtualbox-cpu-count "$DOCKER_VM_CPU_COUNT" \
              --virtualbox-hostonly-cidr "$DOCKER_VM_HOST_CIDR" \
              "$DOCKER_VM_NAME"

  eval $(docker-machine env "$DOCKER_VM_NAME")

  # Node admin expects different names for the certificates. Just symlink docker has 
  # generated for us to match those in node-admin/src/main/application/services.xml.
  (
    cd "$DOCKER_CERT_PATH"
    ln -s ca.pem ca_cert.pem
    ln -s key.pem client_key.pem
    ln -s cert.pem client_cert.pem
  )
  DOCKER_VM_WAS_STARTED=true
fi


VESPA_VM_STATUS=$(docker-machine status "$DOCKER_VM_NAME")
if [ "$VESPA_VM_STATUS" == "Stopped" ]; then
    docker-machine start "$DOCKER_VM_NAME"
    DOCKER_VM_WAS_STARTED=true
    VESPA_VM_STATUS=$(docker-machine status "$DOCKER_VM_NAME")
fi

if [ "$VESPA_VM_STATUS" != "Running" ]; then
  echo "Unable to get Docker machine $DOCKER_VM_NAME up and running."
  echo "You can try to manually remove the machine: docker-machine rm -y $DOCKER_VM_NAME "
  echo "  and then rerun this script."
  echo "Exiting."
  exit 1
fi

if $DOCKER_VM_WAS_STARTED; then
  # Put anything that is not persisted between VM restarts in here.
  # Set up NAT for the $HOST_BRIDGE_INTERFACE interface so that we can connect directly from OS X.
  docker-machine ssh "$DOCKER_VM_NAME" sudo /usr/local/sbin/iptables -t nat -A POSTROUTING -s "$HOST_BRIDGE_NETWORK" ! -o "$HOST_BRIDGE_INTERFACE" -j MASQUERADE
  docker-machine ssh "$DOCKER_VM_NAME" sudo /usr/local/sbin/iptables -A FORWARD -o "$HOST_BRIDGE_INTERFACE" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT

  # Install dependencies used by setup scripts
  docker-machine ssh "$DOCKER_VM_NAME" tce-load -wi python bash
fi

# Get the environment for our VM
eval $(docker-machine env "$DOCKER_VM_NAME")

if [ $# -ge 1 ]; then
  declare -r ARG_SCRIPT=$1
  shift

  declare -r ARG_SCRIPT_BASE=$(basename "$ARG_SCRIPT")
  declare -r ARG_SCRIPT_DIR=$(cd $(dirname "$ARG_SCRIPT") && pwd -P)
  declare -r ARG_SCRIPT_ABS="$ARG_SCRIPT_DIR/$ARG_SCRIPT_BASE"

  if ! docker-machine ssh "$DOCKER_VM_NAME" which "$ARG_SCRIPT_ABS" &> /dev/null; then 
    echo "Provided script file does not exist or is not executable in VM : $ARG_SCRIPT_ABS"
    echo "Usage: $0 [SCRIPT] [SCRIPT_ARGS...]"
    exit 1
  fi

  # Start the provided script. This works because the $HOME directory is mapped in the same location in the VM. 
  docker-machine ssh "$DOCKER_VM_NAME" "CONTAINER_CERT_PATH=$DOCKER_CERT_PATH $ARG_SCRIPT_ABS $*"
fi

