# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcommon
    SOURCES
    $<TARGET_OBJECTS:searchcommon_searchcommon_common>
    $<TARGET_OBJECTS:searchcommon_searchcommon_attribute>
    INSTALL lib64
    DEPENDS
)
