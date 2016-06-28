#!/bin/bash

fail=0

. ../../binref/env.sh

export PORT_0

$BINREF/compilejava MockupInvoke.java || fail=1

bash ./dotest.sh || fail=1

$BINREF/progctl.sh progdefs.sh stop all

exit $fail
