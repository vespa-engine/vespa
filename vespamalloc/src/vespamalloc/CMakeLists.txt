# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespamalloc
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_malloc>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    ${VESPA_ATOMIC_LIB}
    dl
)
vespa_add_library(vespamallocd
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocd>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    ${VESPA_ATOMIC_LIB}
    dl
)
vespa_add_library(vespamallocdst16
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    ${VESPA_ATOMIC_LIB}
    dl
)
vespa_add_library(vespamallocdst16_nl
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mallocdst16_nl>
    $<TARGET_OBJECTS:vespamalloc_util>
    INSTALL lib64/vespa/malloc
    DEPENDS
    ${VESPA_ATOMIC_LIB}
    dl
)
vespa_add_library(vespammap
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_mmap>
    INSTALL lib64/vespa/malloc
    DEPENDS
    dl
)
vespa_add_library(vespa_load_as_huge
    SOURCES
    $<TARGET_OBJECTS:vespamalloc_load_as_huge>
    INSTALL lib64/vespa/malloc
    DEPENDS
    dl
)
