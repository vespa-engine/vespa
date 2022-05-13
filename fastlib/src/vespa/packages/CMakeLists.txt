# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastlib_fast
    SOURCES
    $<TARGET_OBJECTS:fastlib_io>
    $<TARGET_OBJECTS:fastlib_text>
    INSTALL lib64
    DEPENDS
)
