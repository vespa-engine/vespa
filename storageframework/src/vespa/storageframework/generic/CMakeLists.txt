# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageframework_generic
    SOURCES
    $<TARGET_OBJECTS:storageframework_component>
    $<TARGET_OBJECTS:storageframework_status>
    $<TARGET_OBJECTS:storageframework_thread>
    $<TARGET_OBJECTS:storageframework_clock>
    INSTALL lib64
    DEPENDS
)
