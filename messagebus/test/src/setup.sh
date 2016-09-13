#!/bin/sh -e
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
MYDIR=`pwd`
cp ../../src/cpp/Makefile.inc .
cp ../../src/cpp/versiontag.mak .
cp ../../src/cpp/config.cfg .
cp ../../src/cpp/config_command.sh .
sh config_command.sh
echo MODULEDEP_INCLUDES += -I$MYDIR/../../src/cpp >> Makefile.ini
echo LIBDIR_MESSAGEBUS=$MYDIR/../../src/cpp/messagebus >> Makefile.ini
echo LIBDIR_MESSAGEBUS-TEST=$MYDIR/../../src/cpp/messagebus/testlib >> Makefile.ini
