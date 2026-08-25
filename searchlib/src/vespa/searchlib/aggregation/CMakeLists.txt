# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_aggregation OBJECT
    SOURCES
    aggregation.cpp
    countaggregationresult.cpp
    fs4hit.cpp
    group.cpp
    grouping.cpp
    groupinglevel.cpp
    hit.cpp
    hitlist.cpp
    hitsaggregationresult.cpp
    maxaggregationresult.cpp
    modifiers.cpp
    quantile_aggregation_result.cpp
    rawrank.cpp
    sumaggregationresult.cpp
    vdshit.cpp
    DEPENDS
)
