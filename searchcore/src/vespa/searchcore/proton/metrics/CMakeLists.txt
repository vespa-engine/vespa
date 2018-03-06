# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_proton_metrics STATIC
    SOURCES
    attribute_metrics.cpp
    content_proton_metrics.cpp
    documentdb_job_trackers.cpp
    documentdb_metrics_collection.cpp
    documentdb_tagged_metrics.cpp
    executor_metrics.cpp
    executor_threading_service_stats.cpp
    job_load_sampler.cpp
    job_tracker.cpp
    job_tracked_flush_target.cpp
    job_tracked_flush_task.cpp
    legacy_attribute_metrics.cpp
    legacy_documentdb_metrics.cpp
    legacy_proton_metrics.cpp
    memory_usage_metrics.cpp
    metrics_engine.cpp
    resource_usage_metrics.cpp
    sessionmanager_metrics.cpp
    trans_log_server_metrics.cpp
    DEPENDS
)
