# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageframework_defaultimplementation
    SOURCES
    $<TARGET_OBJECTS:storageframework_clockimpl>
    $<TARGET_OBJECTS:storageframework_componentimpl>
    $<TARGET_OBJECTS:storageframework_threadimpl>
    INSTALL lib64
    DEPENDS
    storageframework_generic
)
