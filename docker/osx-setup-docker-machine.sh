#!/bin/bash

DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

DOCKER_VM_NAME=vespa-docker-machine
DOCKER_VM_DISK_SIZE_IN_MB=40000
DOCKER_VM_MEMORY_SIZE_IN_MB=4000
DOCKER_VM_CPU_COUNT=4

DOCKER_VM_WAS_STARTED=false

if ! docker-machine status "$DOCKER_VM_NAME" &> /dev/null; then
  # Machine does not exist and we have to create and start
  docker-machine create -d virtualbox \
              --virtualbox-disk-size "$DOCKER_VM_DISK_SIZE_IN_MB" \
              --virtualbox-memory "$DOCKER_VM_MEMORY_SIZE_IN_MB" \
              --virtualbox-cpu-count "$DOCKER_VM_CPU_COUNT" \
              "$DOCKER_VM_NAME"

  eval $(docker-machine env "$DOCKER_VM_NAME")
  DOCKER_VM_WAS_STARTED=true
fi


VESPA_VM_STATUS=$(docker-machine status "$DOCKER_VM_NAME")
if [ "$VESPA_VM_STATUS" = "Stopped" ]; then
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
  # Hostname should match the public IP
  docker-machine ssh "$DOCKER_VM_NAME" "sudo sed -i \"s/127.0.0.1 $DOCKER_VM_NAME/127.0.0.1/\" /etc/hosts"
  docker-machine ssh "$DOCKER_VM_NAME" "sudo sed -i \"/$DOCKER_VM_NAME/d\" /etc/hosts"
  docker-machine ssh "$DOCKER_VM_NAME" "sudo echo $(docker-machine ip $DOCKER_VM_NAME) $DOCKER_VM_NAME | sudo tee -a /etc/hosts" > /dev/null
fi

eval $(docker-machine env "$DOCKER_VM_NAME")

