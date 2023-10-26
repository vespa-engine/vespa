#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava $SOURCE_DIRECTORY/JavaServer.java
$BINREF/compilejava $SOURCE_DIRECTORY/JavaClient.java

(ulimit -c; ulimit -H -c; ulimit -c unlimited; $VALGRIND ./messagebus_test_speed_test_app)
