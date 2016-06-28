#!/bin/bash
set -e

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

$BINREF/compilejava TestErrors.java

bash -e dotest.sh || (bash -e $BINREF/progctl.sh progdefs.sh stop all; false)
bash -e $BINREF/progctl.sh progdefs.sh stop all
