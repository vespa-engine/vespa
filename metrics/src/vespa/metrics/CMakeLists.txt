# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(metrics
    SOURCES
    metric.cpp
    countmetric.cpp
    valuemetric.cpp
    metrictimer.cpp
    metricset.cpp
    metricmanager.cpp
    metricsnapshot.cpp
    jsonwriter.cpp
    textwriter.cpp
    xmlwriter.cpp
    state_api_adapter.cpp
    INSTALL lib64
    DEPENDS
)
vespa_generate_config(metrics metricsmanager.def)
install(FILES metricsmanager.def DESTINATION var/db/vespa/config_server/serverdb/classes)
