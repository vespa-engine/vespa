#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

declare -r NETWORK_PREFIX=172.18
declare -r CONFIG_SERVER_HOSTNAME=config-server
declare -r CONFIG_SERVER_IP="$NETWORK_PREFIX.1.1"

declare -r APP_HOSTNAME_PREFIX=cnode-
declare -r APP_NETWORK_PREFIX="$NETWORK_PREFIX.2"
declare -r NUM_APP_CONTAINERS=20  # Statically allocated number of nodes.

declare -r SYSTEM_TEST_HOSTNAME_PREFIX=stest-
declare -r SYSTEM_TEST_NETWORK_PREFIX="$NETWORK_PREFIX.3"
declare -r NUM_SYSTEM_TEST_CONTAINERS=5  # Statically allocated number of nodes.

declare -r HOSTS_FILE=/etc/hosts
declare -r HOSTS_LINE_SUFFIX=" # Managed by etc-hosts.sh"

function IsInHostsAlready {
    local ip="$1"
    local hostname="$2"
    local file="$3"

    # TODO: Escape $ip to make sure it's matched as a literal in the regex.
    local matching_ip_line
    matching_ip_line=$(grep -E "^$ip[ \\t]" "$file")

    local -i num_ip_lines=0
    # This 'if' is needed because wc -l <<< "" is 1.
    if [ -n "$matching_ip_line" ]
    then
        num_ip_lines=$(wc -l <<< "$matching_ip_line")
    fi

    local matching_hostname_line
    matching_hostname_line=$(grep -E "^[^#]*[ \\t]$hostname(\$|[ \\t])" "$file")

    local -i num_hostname_lines=0
    # This 'if' is needed because wc -l <<< "" is 1.
    if [ -n "$matching_hostname_line" ]
    then
        num_hostname_lines=$(wc -l <<< "$matching_hostname_line")
    fi

    if ((num_ip_lines == 1)) && ((num_hostname_lines == 1)) &&
           [ "$matching_ip_line" == "$matching_hostname_line" ]
    then
        return 0
    elif ((num_ip_lines == 0)) && ((num_hostname_lines == 0))
    then
        return 1
    else
        printf "$file contains a conflicting host specification for $hostname/$ip"
        exit 1
    fi
}

function AddHost {
    local ip="$1"
    local hostname="$2"
    local file="$3"

    if IsInHostsAlready "$ip" "$hostname" "$file"
    then
        return
    fi

    echo -n "Adding host $hostname ($ip) to $file... "
    printf "%-11s %s%s\n" "$ip" "$hostname" "$HOSTS_LINE_SUFFIX" >> "$file"
    echo done
}

function Stop {
    # TODO: Remove entries.
    :
}

function StartAsRoot {
    # May need sudo
    if [ ! -w "$HOSTS_FILE" ]
    then
        Fail "$HOSTS_FILE is not writeable (run script with sudo)"
    fi

    AddHost "$CONFIG_SERVER_IP" "$CONFIG_SERVER_HOSTNAME" "$HOSTS_FILE"

    local -i index=1
    for ((; index <= NUM_APP_CONTAINERS; ++index))
    do
        local ip="$APP_NETWORK_PREFIX.$index"
        local container_name="$APP_HOSTNAME_PREFIX$index"
        AddHost "$ip" "$container_name" "$HOSTS_FILE"
    done

    local -i index=1
    for ((; index <= NUM_SYSTEM_TEST_CONTAINERS; ++index))
    do
        local ip="$SYSTEM_TEST_NETWORK_PREFIX.$index"
        local container_name="$SYSTEM_TEST_HOSTNAME_PREFIX$index"
        AddHost "$ip" "$container_name" "$HOSTS_FILE"
    done
}

StartAsRoot "$@"
