# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi STATIC
    SOURCES
    $<TARGET_OBJECTS:searchcorespi_flush>
    $<TARGET_OBJECTS:searchcorespi_index>
    DEPENDS
)
