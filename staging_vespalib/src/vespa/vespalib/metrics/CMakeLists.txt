# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_metrics OBJECT
    SOURCES
    clock.cpp
    counter.cpp
    dummy_metrics_collector.cpp
    gauge.cpp
    json_formatter.cpp
    mergers.cpp
    metrics_collector.cpp
    name_collection.cpp
    no_realloc_bunch.cpp
    point.cpp
    producer.cpp
    simple_metrics_collector.cpp
    simple_metrics.cpp
    snapshots.cpp

    DEPENDS
)
