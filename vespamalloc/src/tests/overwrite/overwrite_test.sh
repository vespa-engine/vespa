#!/bin/bash	
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

LD_PRELOAD=../../../src/vespamalloc/libvespamalloc.so ./vespamalloc_overwrite_test_app
LD_PRELOAD=../../../src/vespamalloc/libvespamallocd.so ./vespamalloc_overwrite_test_app testmemoryfill
ulimit -c 0; 
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamallocd.so ./vespamalloc_overwrite_test_app prewrite"
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamallocd.so ./vespamalloc_overwrite_test_app postwrite"
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamallocd.so ./vespamalloc_overwrite_test_app writeafterfree"
