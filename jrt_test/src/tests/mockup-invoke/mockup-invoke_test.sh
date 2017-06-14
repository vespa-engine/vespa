#!/bin/bash

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

fail=0

. ../../binref/env.sh

export PORT_0

$BINREF/compilejava -d . $SOURCE_DIRECTORY/MockupInvoke.java || fail=1

bash $SOURCE_DIRECTORY/dotest.sh || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop all

exit $fail
