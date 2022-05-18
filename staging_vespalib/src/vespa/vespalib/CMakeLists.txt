# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib
    SOURCES
    $<TARGET_OBJECTS:staging_vespalib_vespalib_util>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_objects>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_stllike>
    INSTALL lib64
    DEPENDS
)
