# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi_flush STATIC OBJECT
    SOURCES
    flushstats.cpp
    iflushtarget.cpp
    DEPENDS
)
