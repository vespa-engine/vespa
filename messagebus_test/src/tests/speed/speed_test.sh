#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava $SOURCE_DIRECTORY/JavaServer.java
$BINREF/compilejava $SOURCE_DIRECTORY/JavaClient.java

(ulimit -c; ulimit -H -c; ulimit -c unlimited; $VALGRIND ./messagebus_test_speed_test_app)
