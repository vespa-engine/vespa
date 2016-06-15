#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

echo "This will alter your routing table and /etc/hosts file. Continue ?"
select yn in "Yes" "No"; do
    case $yn in
        Yes ) break;;
        No ) echo "Exiting."; exit;;
    esac
done

# Setup the route
cd "$SCRIPT_DIR"
./route-osx.sh

# Setup the hosts file
cd "$SCRIPT_DIR"
./etc-hosts.sh
