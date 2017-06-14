#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

declare -r VESPA_WEB_SERVICE_PORT=4080

# Output from InnerCurlNodeRepo, see there for details.
declare CURL_RESPONSE

function Usage {
    cat <<EOF
Usage: ${0##*/} <command> [<args>...]
Script for manipulating the Node Repository.

Commands
    add [-c <configserverhost>] -p <parent-hostname> [-f <parent-flavor>]
        [-n <flavor> <hostname>...]
        With -f, provision "host" node <parent-hostname> with flavor
        <parent-flavor>. With -n, provision "tenant" nodes <hostname...> with
        flavor <flavor> and parent host <parenthostname>.
    reprovision [-c <configserverhost>] -p <parenthostname> <hostname>...
        Fail node <hostname>, then rm and add.
    rm [-c <configserverhost>] <hostname>...
        Remove nodes from node repo.
    set-state [-c <configserverhost>] <state> <hostname>...
        Set the node repo state.

By default, <configserverhost> is config-server.

Example
    To remove docker-1--1 through docker-1--5 from the node repo at configserver.com,

        ${0##*/} rm -c configserver.com \
            docker-1--{1,2,3,4,5}.dockerhosts.com
EOF

    exit 1
}

function Fail {
    printf "%s\nRun '${0##*/} help' for usage\n" "$*"
    exit 1
}

# Invoke curl such that:
#
# - Arguments to this function are passed to curl.
#
# - Additional arguments are passed to curl to filter noise (--silent
#   --show-error).
#
# - The curl stdout (i.e. the response) is stored in the global CURL_RESPONSE
#   variable on return of the function.
#
# - If curl returns 0 (i.e. a successful HTTP response) with a JSON response
#   that contains an "error-code" key, this function will instead return 22. 22
#   does not conflict with a curl return code, because curl only ever returns
#   22 when --fail is specified.
#
#   Except, if the JSON response contains a "message" of the form "Cannot add
#   tmp-cnode-0: A node with this name already exists", InnerCurlNodeRepo
#   returns 0 instead of 22, even if the HTTP response status code is an error.
#
#   Note: Why not use --fail with curl? Because as of 2015-11-24, if the node
#   exists when provisioning a node, the node repo returns a 400 Bad Request
#   with a JSON body containing a "message" field as described above. With
#   --fail, this would result in curl exiting with code 22, which is
#   indistinguishable from other HTTP errors. Can the output from --show-error
#   be used in combination with --fail? No, because that ends up saying "curl:
#   (22) The requested URL returned error: 400 Bad Request" when the node
#   exists, making it indistinguishable from other malformed request error
#   messages.
#
#   TODO: Make node repo return a unique HTTP error code when node already
#   exists. It's also fragile to test for the error message in the response.
function InnerCurlNodeRepo {
    # --show-error causes error message to be printed on error, even with
    # --silent, which is useful when we print the error message to Fail.
    local -a command=(curl --silent --show-error "$@")

    # We need the 'if' here, because a non-zero exit code of a command will
    # exit the process, with 'set -e'.
    if CURL_RESPONSE=$("${command[@]}" 2>&1)
    then
        # Match a JSON of the form:
        #   {
        #     "error-code": "BAD_REQUEST",
        #     "message": "Cannot add cnode-0: A node with this name already exists"
        #   }
        if [[ "$CURL_RESPONSE" =~ '"error-code"' ]]
        then
            if [[ "$CURL_RESPONSE" =~ '"message"'[^\"]*\"(.*)\" ]]
            then
                local message="${BASH_REMATCH[1]}"
                if [[ "$message" =~ 'already exists' ]]
                then
                    return 0
                fi
            fi

            return 22
        fi

        return 0
    else
        # Do not move this statement outside of this else: $? gets cleared when
        # the execution passes out of the else-fi block.
        return $?
    fi
}

function CurlOrFail {
    if InnerCurlNodeRepo "$@"
    then
        : # This form of if-else is used to preserve $?.
    else
        local error_code=$?

        # Terminate the current progress-bar-like line
        printf ' failed\n'

        Fail "Error ($error_code) from the node repo at '$url': '$CURL_RESPONSE'"
    fi
}

function ProvisionDockerNode {
    local config_server_hostname="$1"
    local container_hostname="$2"
    local parent_hostname="$3"
    local flavor="$4"

    local json="[
                  {
                    \"hostname\":\"$container_hostname\",
                    \"parentHostname\":\"$parent_hostname\",
                    \"openStackId\":\"fake-$container_hostname\",
                    \"flavor\":\"$flavor\",
                    \"type\":\"tenant\"
                  }
                ]"

    ProvisionNode $config_server_hostname "$json"
}


# Docker host, the docker nodes points to this host in parentHostname in their node config
function ProvisionDockerHost {
    local config_server_hostname="$1"
    local docker_host_hostname="$2"
    local flavor="$3"

    local json="[
                  {
                    \"hostname\":\"$docker_host_hostname\",
                    \"openStackId\":\"$docker_host_hostname\",
                    \"flavor\":\"$flavor\",
                    \"type\":\"host\"
                  }
                ]"

    ProvisionNode $config_server_hostname "$json"
}

# Docker node in node repo (both docker hosts and docker nodes)
function ProvisionNode {
    local config_server_hostname="$1"
    local json="$2"

    local url="http://$config_server_hostname:$VESPA_WEB_SERVICE_PORT/nodes/v2/node"

    CurlOrFail -H "Content-Type: application/json" -X POST -d "$json" "$url"
}

function SetNodeState {
    local config_server_hostname="$1"
    local hostname="$2"
    local state="$3"

    local url="http://$config_server_hostname:$VESPA_WEB_SERVICE_PORT/nodes/v2/state/$state/$hostname"
    CurlOrFail -X PUT "$url"
}

function AddCommand {
    local config_server_hostname=config-server
    local parent_hostname=

    OPTIND=1
    local option
    while getopts "c:p:f:n:" option
    do
        case "$option" in
            c) config_server_hostname="$OPTARG" ;;
            p) parent_hostname="$OPTARG" ;;
            f) parent_host_flavor="$OPTARG" ;;
            n) node_flavor="$OPTARG" ;;
            ?) exit 1 ;; # E.g. option lacks argument, in case error has been
                         # already been printed
            *) Fail "Unknown option '$option' with value '$OPTARG'"
        esac
    done

    if [ -z "$parent_hostname" ]
    then
        Fail "Parent hostname not specified (-p)"
    fi

    shift $((OPTIND - 1))

    if [ -n "$parent_host_flavor" ]
    then
        echo "Provisioning Docker host $parent_hostname with flavor $parent_host_flavor"
        ProvisionDockerHost "$config_server_hostname" \
                            "$parent_hostname" \
                            "$parent_host_flavor"
    fi

    if [ -n "$node_flavor" ]
    then
        echo -n "Provisioning $# nodes with parent host $parent_hostname"
        local container_hostname
        for container_hostname in "$@"
        do
            ProvisionDockerNode "$config_server_hostname" \
                                "$container_hostname" \
                                "$parent_hostname" \
                                "$node_flavor"
            echo -n .
        done

        echo " done"
    fi
}

function ReprovisionCommand {
    local config_server_hostname=config-server
    local parent_hostname=

    OPTIND=1
    local option
    while getopts "c:p:" option
    do
        case "$option" in
            c) config_server_hostname="$OPTARG" ;;
            p) parent_hostname="$OPTARG" ;;
            ?) exit 1 ;; # E.g. option lacks argument, in case error has been
                         # already been printed
            *) Fail "Unknown option '$option' with value '$OPTARG'"
        esac
    done

    if [ -z "$parent_hostname" ]
    then
        Fail "Parent hostname not specified (-p)"
    fi

    shift $((OPTIND - 1))

    if (($# == 0))
    then
        Fail "No node hostnames were specified"
    fi

    # Simulate calls to the following commands.
    SetStateCommand -c "$config_server_hostname" failed "$@"
    RemoveCommand -c "$config_server_hostname" "$@"
    AddCommand -c "$config_server_hostname" -p "$parent_hostname" "$@"
}

function RemoveCommand {
    local config_server_hostname=config-server

    OPTIND=1
    local option
    while getopts "c:" option
    do
        case "$option" in
            c) config_server_hostname="$OPTARG" ;;
            ?) exit 1 ;; # E.g. option lacks argument, in case error has been
                         # already been printed
            *) Fail "Unknown option '$option' with value '$OPTARG'"
        esac
    done

    shift $((OPTIND - 1))

    if (($# == 0))
    then
        Fail "No nodes were specified"
    fi

    echo -n "Removing $# nodes"

    local hostname
    for hostname in "$@"
    do
        local url="http://$config_server_hostname:$VESPA_WEB_SERVICE_PORT/nodes/v2/node/$hostname"
        CurlOrFail -X DELETE "$url"
        echo -n .
    done

    echo " done"
}

function SetStateCommand {
    local config_server_hostname=config-server

    OPTIND=1
    local option
    while getopts "c:" option
    do
        case "$option" in
            c) config_server_hostname="$OPTARG" ;;
            ?) exit 1 ;; # E.g. option lacks argument, in case error has been
                         # already been printed
            *) Fail "Unknown option '$option' with value '$OPTARG'"
        esac
    done

    shift $((OPTIND - 1))

    if (($# <= 1))
    then
        Fail "Too few arguments"
    fi

    local state="$1"
    shift

    echo -n "Setting $# nodes to $state"

    local hostname
    for hostname in "$@"
    do
        SetNodeState "$config_server_hostname" "$hostname" "$state"
        echo -n .
    done

    echo " done"
}

function Main {
    if (($# == 0))
    then
        Usage
    fi
    local command="$1"
    shift

    case "$command" in
        add) AddCommand "$@" ;;
        reprovision) ReprovisionCommand "$@" ;;
        rm) RemoveCommand "$@" ;;
        set-state) SetStateCommand "$@" ;;
        help) Usage "$@" ;;
        *) Usage ;;
    esac
}

Main "$@"
