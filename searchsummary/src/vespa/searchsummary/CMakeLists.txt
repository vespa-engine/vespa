# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchsummary
    SOURCES
    $<TARGET_OBJECTS:searchsummary_config>
    $<TARGET_OBJECTS:searchsummary_docsummary>
    $<TARGET_OBJECTS:searchsummary_juniper>
    INSTALL lib64
    DEPENDS
    z
)
