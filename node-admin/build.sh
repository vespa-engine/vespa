#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

function Usage {
    cat <<EOF >&2
$*

Usage: build.sh
Builds the local node-admin docker image used in the local zone.

EOF
    exit 1
}

if [ -z "$NODE_ADMIN_FROM_IMAGE" ]
then
    Usage "You must set the NODE_ADMIN_FROM_IMAGE environment variable to point to the base image (FROM-line in Dockerfile) you'd like to build the node admin image on."
elif [[ "$NODE_ADMIN_FROM_IMAGE" =~ % ]]
then
    Usage "NODE_ADMIN_FROM_IMAGE environment variable cannot contain the %-character."
elif [ -z "$VESPA_HOME" ]
then
    Usage "VESPA_HOME environment variable is not set."
fi

cat Dockerfile.template | \
    sed 's%$NODE_ADMIN_FROM_IMAGE%'"$NODE_ADMIN_FROM_IMAGE%g" | \
    sed 's%$VESPA_HOME%'"$VESPA_HOME%g" \
    > Dockerfile

docker build --tag="vespa-local:latest" .
