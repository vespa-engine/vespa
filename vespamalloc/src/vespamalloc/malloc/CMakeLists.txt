# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespamalloc_malloc OBJECT
    SOURCES
    malloc.cpp
    allocchunk.cpp
    common.cpp
    freelist.cpp
    mmappool.cpp
    threadproxy.cpp
    memblock.cpp
    datasegment.cpp
    globalpool.cpp
    threadpool.cpp
    threadlist.cpp
    DEPENDS
)
vespa_add_library(vespamalloc_mallocd OBJECT
    SOURCES
    mallocd.cpp
    allocchunk.cpp
    common.cpp
    freelist.cpp
    mmappool.cpp
    threadproxy.cpp
    memblockboundscheck.cpp
    memblockboundscheck_d.cpp
    datasegment.cpp
    globalpoold.cpp
    threadpoold.cpp
    threadlistd.cpp
    DEPENDS
)
vespa_add_library(vespamalloc_mallocdst16 OBJECT
    SOURCES
    mallocdst16.cpp
    allocchunk.cpp
    common.cpp
    freelist.cpp
    mmappool.cpp
    threadproxy.cpp
    memblockboundscheck.cpp
    memblockboundscheck_dst.cpp
    datasegment.cpp
    globalpooldst.cpp
    threadpooldst.cpp
    threadlistdst.cpp
    DEPENDS
)
vespa_workaround_gcc_bug_67055(mallocdst16.cpp)
vespa_add_library(vespamalloc_mallocdst16_nl OBJECT
    SOURCES
    mallocdst16_nl.cpp
    allocchunk.cpp
    common.cpp
    freelist.cpp
    mmappool.cpp
    threadproxy.cpp
    memblockboundscheck.cpp
    memblockboundscheck_dst.cpp
    datasegment.cpp
    globalpooldst.cpp
    threadpooldst.cpp
    threadlistdst.cpp
    DEPENDS
)
vespa_workaround_gcc_bug_67055(mallocdst16_nl.cpp)
vespa_add_library(vespamalloc_mmap OBJECT
    SOURCES
    mmap.cpp
    DEPENDS
)
vespa_add_library(vespamalloc_load_as_huge OBJECT
    SOURCES
    load_as_huge.cpp
    DEPENDS
)
