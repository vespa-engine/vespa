# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_grouping STATIC
    SOURCES
    fast_map_lookups.cpp
    groupingcontext.cpp
    groupingmanager.cpp
    groupingsession.cpp
    DEPENDS
)
