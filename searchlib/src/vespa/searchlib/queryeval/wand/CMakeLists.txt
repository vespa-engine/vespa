# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_queryeval_wand OBJECT
    SOURCES
    parallel_weak_and_blueprint.cpp
    parallel_weak_and_search.cpp
    wand_parts.cpp
    weak_and_heap.cpp
    weak_and_search.cpp
    DEPENDS
)
