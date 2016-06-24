#!/bin/bash
set -e
. ../../binref/env.sh
sh dotest.sh || (sh $BINREF/progctl.sh progdefs.sh stop all; false)
sh $BINREF/progctl.sh progdefs.sh stop all
