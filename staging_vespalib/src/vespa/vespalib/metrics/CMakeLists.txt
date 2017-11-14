# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_metrics OBJECT
    SOURCES
    name_collection.cpp
    no_realloc_bunch.cpp
    simple_metrics.cpp
    simple_metrics_collector.cpp
    DEPENDS
)
