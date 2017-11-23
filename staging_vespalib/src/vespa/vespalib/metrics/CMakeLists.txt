# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_metrics OBJECT
    SOURCES
    axis.cpp
    clock.cpp
    coordinate.cpp
    counter.cpp
    dummy_metrics_manager.cpp
    gauge.cpp
    json_formatter.cpp
    mergers.cpp
    metric_identifier.cpp
    metrics_manager.cpp
    metric_types.cpp
    name_collection.cpp
    no_realloc_bunch.cpp
    point.cpp
    point_builder.cpp
    point_map.cpp
    producer.cpp
    simple_metrics.cpp
    simple_metrics_manager.cpp
    snapshots.cpp

    DEPENDS
)
