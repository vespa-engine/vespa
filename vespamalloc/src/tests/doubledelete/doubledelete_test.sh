#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

LD_PRELOAD=../../../src/vespamalloc/libvespamalloc.so ./vespamalloc_doubledelete_test_app

ulimit -c 0 
./vespamalloc_expectsignal_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamalloc_vespamallocd.so ./vespamalloc_doubledelete_test_app"
