#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

echo "Trying to find limit for processes:"
if ulimit -u; then
    echo "Fixing limit to 14100"
    ulimit -u 14100
elif [ "$RETRYEXEC" ]; then
    echo "Already tried to re-exec script, giving up."
    exit 1
else
    echo "Command 'ulimit -u' failed, trying to re-exec script with bash instead."
    exec /usr/bin/env RETRYEXEC=true bash $0
fi

VESPA_MALLOC_SO=../../../src/vespamalloc/libvespamalloc.so
VESPA_MALLOC_SO_D=../../../src/vespamalloc/libvespamallocd.so

#LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app return 20
#LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app exit 20
#LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_thread_test_app cancel 20
#LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_racemanythreads_test_app 4000 20
#LD_PRELOAD=$VESPA_MALLOC_SO_D ./vespamalloc_racemanythreads_test_app 4000 20
