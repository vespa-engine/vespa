#!/bin/bash
set -e

. ../../binref/env.sh

export PORT_0

$BINREF/compilejava MockupInvoke.java

bash -e dotest.sh || (bash -e $BINREF/progctl.sh progdefs.sh stop all; false)
bash -e $BINREF/progctl.sh progdefs.sh stop all
