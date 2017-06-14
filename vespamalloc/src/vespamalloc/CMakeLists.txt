# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespamalloc
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_malloc>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64
    DEPENDS
    dl
)
vespa_add_library(vespamalloc_vespamallocd
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocd>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64
    DEPENDS
    dl
)
vespa_add_library(vespamalloc_vespamallocdst16
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64
    DEPENDS
    dl
)
vespa_add_library(vespamalloc_vespamallocdst16_nl
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16_nl>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64
    DEPENDS
    dl
)
vespa_add_library(vespamalloc_vespammap
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mmap>
    INSTALL lib64
    DEPENDS
)
