#!/bin/bash
. ../../binref/env.sh
sh dotest.sh || (sh $BINREF/progctl.sh progdefs.sh stop all; false)
sh $BINREF/progctl.sh progdefs.sh stop all
