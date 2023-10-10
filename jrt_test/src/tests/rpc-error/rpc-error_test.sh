#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

fail=0

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

$BINREF/compilejava -d . $SOURCE_DIRECTORY/TestErrors.java || fail=1

bash $SOURCE_DIRECTORY/dotest.sh || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop all

exit $fail
