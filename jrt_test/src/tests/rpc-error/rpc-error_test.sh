#!/bin/bash

fail=0

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

$BINREF/compilejava TestErrors.java || fail=1

bash ./dotest.sh || fail=1

$BINREF/progctl.sh progdefs.sh stop all

exit $fail
