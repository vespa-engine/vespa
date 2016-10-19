# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# Common variables and functions that may be useful for scripts IN THIS
# DIRECTORY. Should be sourced as follows:
#
#   source "${0%/*}/common.sh"
#
# WARNING: Some system variables, like the Config Server's, are also hardcoded
# in the Docker image startup scripts.

declare -r SCRIPT_NAME="${0##*/}"
declare -r SCRIPT_DIR="${0%/*}"

# TODO: Find a better name. Consider having separate images for config-server
# and node-admin.
declare -r DOCKER_IMAGE="vespa-local:latest"
declare -r APPLICATION_STORAGE_ROOT="/home/docker/container-storage"
declare -r ROOT_DIR_SHARED_WITH_HOST=shared

# The 172.18.0.0/16 network is in IPDB.
declare -r NETWORK_PREFIX=172.18
declare -r NETWORK_PREFIX_BITLENGTH=16

# Hostnames, IP addresses, names, etc of the infrastructure containers.
declare -r HOST_BRIDGE_INTERFACE=vespa
declare -r HOST_BRIDGE_IP="$NETWORK_PREFIX.0.1"
declare -r HOST_BRIDGE_NETWORK="$NETWORK_PREFIX.0.0/$NETWORK_PREFIX_BITLENGTH"
declare -r NODE_ADMIN_CONTAINER_NAME=node-admin
declare -r CONFIG_SERVER_CONTAINER_NAME=config-server
declare -r CONFIG_SERVER_HOSTNAME="$CONFIG_SERVER_CONTAINER_NAME"
declare -r CONFIG_SERVER_IP="$NETWORK_PREFIX.1.1"
declare -r VESPA_WEB_SERVICE_PORT=4080 # E.g. config server port

declare -r DEFAULT_HOSTED_VESPA_REGION=local-region
declare -r DEFAULT_HOSTED_VESPA_ENVIRONMENT=prod

# Hostnames, IP addresses, names, etc of the application containers.  Hostname
# and container names are of the form $PREFIX$N, where N is a number between 1
# and $NUM_APP_CONTAINERS. The IP is $APP_NETWORK_PREFIX.$N.
declare -r APP_NETWORK_PREFIX="$NETWORK_PREFIX.2"
declare -r APP_CONTAINER_NAME_PREFIX=cnode-
declare -r APP_HOSTNAME_PREFIX="$APP_CONTAINER_NAME_PREFIX"
declare -r DEFAULT_NUM_APP_CONTAINERS=20  # Statically allocated number of nodes.
declare -r TENANT_NAME=localtenant

# Allowed program opions
declare OPTION_NUM_NODES          # Set from --num-nodes or DEFAULT_NUM_APP_CONTAINERS, see Main.
declare OPTION_WAIT               # Set from --wait or true, see Main.
declare OPTION_HV_REGION          # Set from --hv-region or DEFAULT_HOSTED_VESPA_REGION, see Main.
declare OPTION_HV_ENV             # Set from --hv-env or DEFAULT_HOSTED_VESPA_ENVIRONMENT, see Main.

declare NUM_APP_CONTAINERS    # Set from OPTION_NUM_NODES or DEFAULT_NUM_APP_CONTAINERS, see Main.

function Fail {
    printf "%s\n" "$@" >&2
    exit 1
}

# Used to help scripts with implementing the Usage function. The intended usage
# is:
#
#   function Usage {
#       UsageHelper "$@" <<EOF
#   Usage: $SCRIPT_NAME ...
#   ...
#   EOF
#   }
#
# When Usage is called, any arguments passed will be printed to stderr, then
# the usage-string will be printed (on stdin for UsageHelper), then the process
# will exit with code 1.
function UsageHelper {
    exec >&2

    if (($# > 0))
    then
        printf "%s\n\n" "$*"
    fi

    # Print to stdout (which has been redirected to stderr) what's on
    # stdin. This will print the usage-string.
    cat

    exit 1
}

# See Main
function Restart {
    Stop
    Start "$@"
}

# Use Main as follows:
#
# Pass all script arguments to Main:
#
#   Main "$@"
#
# Main will parse the arguments as follows. It assumes the arguments have
# the following form:
#
#   script.sh <command> [<arg> | <option>]...
#
# where <command> is one of start, stop, or restart:
#   start: The script MUST define a Start function.
#   stop: The script MUST define a Stop function.
#   restart: common.sh defines a Restart function to mean Stop, then Start.
#
# <arg> cannot start with a dash, and will get passed as argument to the Start
# function (if applicable).
#
# <option> is either of the form --<name>=<value> or --<name> <value>.
# <name>/<value> denotes a set of options. For each option, it sets one of the
# predefined global OPTION_* options.
#
# Having parsed the arguments, Main then calls Start, Restart, or Stop,
# depending on the command. These functions must be defined by the script.
#
# A function Usage must also be defined, which will be called when there's a
# usage error.
function Main {
    # Default command is start
    local command=start
    if (($# > 0)) && ! [[ "$1" =~ ^- ]]
    then
        command="$1"
        shift
    fi

    local -a args=()

    while (($# > 0))
    do
        if [[ "$1" =~ ^--([a-z0-9][a-z0-9-]*)(=(.*))?$ ]]
        then
            # Option argument
            local name="${BASH_REMATCH[1]}"
            shift

            if ((${#BASH_REMATCH[2]} > 0))
            then
                local value="${BASH_REMATCH[3]}"
            else
                if (($# == 0))
                then
                    Usage "Option '$name' missing value"
                fi

                value="$1"
                shift
            fi

            case "$name" in
                num-nodes) OPTION_NUM_NODES="$value" ;;
                wait) OPTION_WAIT="$value" ;;
                hv-region) OPTION_HV_REGION="$value" ;;
                hv-env) OPTION_HV_ENV="$value" ;;
            esac
        elif [[ "$1" =~ ^[^-] ]]
        then
            # Non-option argument
            args+=("$1")
            shift
        else
            Usage "Bad argument '$1'"
        fi
    done

    NUM_APP_CONTAINERS="${OPTION_NUM_NODES:-$DEFAULT_NUM_APP_CONTAINERS}"

    case "$command" in
        help) Usage ;;
        stop) Stop ;;
        start) Start "${args[@]}" ;;
        restart) Restart "${args[@]}" ;;
        *) Usage "Unknown command '$command'"
    esac
}
