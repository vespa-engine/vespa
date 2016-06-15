#!/bin/sh

. ../../binref/env.sh

STATUS=ok
JAVA_PORT=$PORT_3
CPP_PORT=$PORT_4

export JAVA_PORT
export CPP_PORT

$BINREF/compilejava TestErrors.java

sh dotest.sh || (sh $BINREF/progctl.sh progdefs.sh stop all; false)
sh $BINREF/progctl.sh progdefs.sh stop all

