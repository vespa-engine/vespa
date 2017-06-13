# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributormaintenance OBJECT
    SOURCES
    simplebucketprioritydatabase.cpp
    simplemaintenancescanner.cpp
    prioritizedbucket.cpp
    maintenancescheduler.cpp
    node_maintenance_stats_tracker.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
