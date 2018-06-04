# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespamalloc
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_malloc>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    atomic
    dl
)
vespa_add_library(vespamallocd
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocd>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    atomic
    dl
)
vespa_add_library(vespamallocdst16
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    atomic
    dl
)
vespa_add_library(vespamallocdst16_nl
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16_nl>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    atomic
    dl
)
vespa_add_library(vespammap
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mmap>
    INSTALL lib64/vespa/malloc
    DEPENDS
    dl
)
