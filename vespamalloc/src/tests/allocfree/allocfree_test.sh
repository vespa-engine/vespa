#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

TIME=/usr/bin/time

VESPA_MALLOC_SO=../../../src/vespamalloc/libvespamalloc.so
VESPA_MALLOC_SO_D=../../../src/vespamalloc/libvespamalloc_vespamallocd.so

LD_PRELOAD=$VESPA_MALLOC_SO ./vespamalloc_realloc_test_app
LD_PRELOAD=$VESPA_MALLOC_SO_D ./vespamalloc_realloc_test_app
$TIME ./vespamalloc_linklist_test_app 3
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 3
LD_PRELOAD=$VESPA_MALLOC_SO_D $TIME ./vespamalloc_allocfree_shared_test_app 3
$TIME ./vespamalloc_allocfree_shared_test_app 3
VESPA_MALLOC_MADVISE_LIMIT=0x200000 LD_PRELOAD=$VESPA_MALLOC_SO_D $TIME ./vespamalloc_allocfree_shared_test_app 3
LD_PRELOAD=$VESPA_MALLOC_SO_D $TIME ./vespamalloc_allocfree_shared_test_app 3
VESPA_MALLOC_MADVISE_LIMIT=0x200000 VESPA_MALLOC_HUGEPAGES=on LD_PRELOAD=$VESPA_MALLOC_SO_D $TIME ./vespamalloc_allocfree_shared_test_app 3
VESPA_MALLOC_HUGEPAGES=on LD_PRELOAD=$VESPA_MALLOC_SO_D $TIME ./vespamalloc_allocfree_shared_test_app 3
