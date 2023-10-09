#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

TIME=/usr/bin/time

VESPA_MALLOC_SO=../../../src/vespamalloc/libvespamalloc.so
LIBDIR=$LIBDIR

$TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
$TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
$TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
$TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
$TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
$TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
$TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
$TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
$TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
$TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
$TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
$TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
$TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
$TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
$TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
$TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
$TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
$TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$VESPA_MALLOC_SO $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libtcmalloc_minimal.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libjemalloc_mt.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libptmalloc3.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libnedmalloc.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libhoard.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 0
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  1
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  2
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  4
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0  8
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 16
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  0 32
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  1  1
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  2  2
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  4  4
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5  8  8
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5 16 16
LD_PRELOAD=$LIBDIR/libtlsf.so $TIME ./vespamalloc_allocfree_shared_test_app 5 32 32
