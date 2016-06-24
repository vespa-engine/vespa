#!/bin/bash
set -e

. ../../binref/env.sh

export PORT_0

$BINREF/compilejava MockupInvoke.java

sh dotest.sh || (sh $BINREF/progctl.sh progdefs.sh stop all; false)
sh $BINREF/progctl.sh progdefs.sh stop all
