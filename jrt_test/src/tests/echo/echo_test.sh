#!/bin/bash
fail=0
. ../../binref/env.sh
bash ./dotest.sh || fail=1
$BINREF/progctl.sh progdefs.sh stop all
exit $fail
