#!/bin/bash	
set -e

LD_PRELOAD=../../../src/vespamalloc/libvespamalloc.so ./vespamalloc_overwrite_test_app
LD_PRELOAD=../../../src/vespamalloc/libvespamalloc_vespamallocd.so ./vespamalloc_overwrite_test_app testmemoryfill
ulimit -c 0; 
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamalloc_vespamallocd.so ./vespamalloc_overwrite_test_app prewrite"
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamalloc_vespamallocd.so ./vespamalloc_overwrite_test_app postwrite"
./vespamalloc_expectsignal-overwrite_app 6 "LD_PRELOAD=../../../src/vespamalloc/libvespamalloc_vespamallocd.so ./vespamalloc_overwrite_test_app writeafterfree"
