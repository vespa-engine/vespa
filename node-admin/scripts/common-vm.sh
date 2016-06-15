#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

# VM configuration
declare -r DOCKER_VM_NAME=vespa                # Don't put spaces in the name
declare -r DOCKER_VM_DISK_SIZE_IN_MB=40000
declare -r DOCKER_VM_MEMORY_SIZE_IN_MB=4096
declare -r DOCKER_VM_CPU_COUNT=1
declare -r DOCKER_VM_HOST_CIDR=172.21.46.1/24
