# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_metrics OBJECT
    SOURCES
    bucket.cpp
    clock.cpp
    counter_aggregator.cpp
    counter.cpp
    current_samples.cpp
    dimension.cpp
    dummy_metrics_manager.cpp
    gauge_aggregator.cpp
    gauge.cpp
    handle.cpp
    json_formatter.cpp
    label.cpp
    metric_id.cpp
    metrics_manager.cpp
    metric_types.cpp
    name_collection.cpp
    name_repo.cpp
    point_builder.cpp
    point.cpp
    point_map_collection.cpp
    point_map.cpp
    producer.cpp
    simple_metrics.cpp
    simple_metrics_manager.cpp
    simple_tick.cpp
    snapshots.cpp
    stable_store.cpp

    DEPENDS
)
