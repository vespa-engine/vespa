#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

source "${0%/*}/common.sh"

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command>
Make localhost look like a container for the purpose of various other scripts.

Commands:
  start     Make /host/proc point to /proc
  stop      Remove /host directory
  restart   Stop, then start
EOF
}

function MakeHostDirectory {
    if ! [ -e /host ]
    then
        echo "Created directory /host"
        sudo mkdir /host
        if ! [ -e /host/proc ]
        then
            echo "Created symbolic link from /host/proc to /proc"
            sudo ln -s /proc /host/proc
        fi
    fi
}

function RemoveHostDirectory {
    if [ -d /host ]
    then
        echo "Removed /host directory"
        sudo rm -rf /host
    fi
}

function Stop {
    sudo true # Prime sudo

    RemoveHostDirectory
}

function Start {
    sudo true # Prime sudo
    MakeHostDirectory
}

Main "$@"
