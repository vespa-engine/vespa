#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

exec $SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh "$@"
