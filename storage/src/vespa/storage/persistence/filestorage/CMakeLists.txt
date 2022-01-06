# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_filestorpersistence OBJECT
    SOURCES
    active_operations_metrics.cpp
    active_operations_stats.cpp
    filestorhandler.cpp
    filestorhandlerimpl.cpp
    filestormanager.cpp
    filestormetrics.cpp
    has_mask_remapper.cpp
    merge_handler_metrics.cpp
    mergestatus.cpp
    modifiedbucketchecker.cpp
    service_layer_host_info_reporter.cpp
    DEPENDS
)
