# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib
    SOURCES
    $<TARGET_OBJECTS:vdslib_container>
    $<TARGET_OBJECTS:vdslib_state>
    $<TARGET_OBJECTS:vdslib_distribution>
    INSTALL lib64
    DEPENDS
)
