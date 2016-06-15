#!/bin/bash

. ../../binref/env.sh
export PORT_1

$BINREF/compilejava RPCServer.java

sh dotest.sh || (sh $BINREF/progctl.sh progdefs.sh stop all; false)
sh $BINREF/progctl.sh progdefs.sh stop all

