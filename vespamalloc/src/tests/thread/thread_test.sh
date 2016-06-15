#!/bin/bash

VESPA_MALLOC_SO=../../../src/vespamalloc/libvespamalloc.so
VESPA_MALLOC_SO_D=../../../src/vespamalloc/libvespamalloc_vespamallocd.so

LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app return 20
LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app exit 20
LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app cancel 20
#LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_racemanythreads_test_app 4000 20
#LD_PRELOAD=$VESPA_MALLOC_SO_D ./vespamalloc_racemanythreads_test_app 4000 20
