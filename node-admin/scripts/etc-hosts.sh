#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

declare -r HOSTS_FILE=/etc/hosts
declare -r HOSTS_LINE_SUFFIX=" # Managed by etc-hosts.sh"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command> [--num-nodes <num-nodes>]
Manage Docker container DNS<->IP resolution ($HOSTS_FILE).

Commands:
  start     Add Docker containers to $HOSTS_FILE
  stop      Remove Docker containers from $HOSTS_FILE (not implemented)
  restart   Stop, then start

Options:
  --num-nodes <num-nodes>
            Add <num-nodes> hosts instead of the default $DEFAULT_NUM_APP_CONTAINERS.
EOF
}

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
        Fail "$file contains a conflicting host specification for $hostname/$ip"
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
    if (($# != 0))
    then
        Usage
    fi

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
}

function Start {
    if [ "$(id -u)" != 0 ]
    then
        sudo "$0" "$@"
    else
        StartAsRoot "$@"
    fi
}

Main "$@"
