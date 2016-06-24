#!/bin/bash
. ../../binref/env.sh
export PORT_1

$BINREF/compilejava RPCServer.java

bash -e dotest.sh || (bash -e $BINREF/progctl.sh progdefs.sh stop all; false)
bash -e $BINREF/progctl.sh progdefs.sh stop all
