# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib
    SOURCES
    $<TARGET_OBJECTS:staging_vespalib_vespalib_encoding>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_util>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_data>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_metrics>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_objects>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_stllike>
    $<TARGET_OBJECTS:staging_vespalib_vespalib_net>
    INSTALL lib64
    DEPENDS
)
