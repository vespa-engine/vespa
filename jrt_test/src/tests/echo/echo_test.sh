#!/bin/bash
set -e
. ../../binref/env.sh
bash -e dotest.sh || (bash -e $BINREF/progctl.sh progdefs.sh stop all; false)
bash -e $BINREF/progctl.sh progdefs.sh stop all
