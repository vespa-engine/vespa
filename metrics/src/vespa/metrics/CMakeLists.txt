# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(metrics
    SOURCES
    countmetric.cpp
    countmetricvalues.cpp
    jsonwriter.cpp
    loadmetric.cpp
    metric.cpp
    memoryconsumption.cpp
    metricmanager.cpp
    metricset.cpp
    metricsnapshot.cpp
    metrictimer.cpp
    metricvalueset.cpp
    namehash.cpp
    printutils.cpp
    state_api_adapter.cpp
    summetric.cpp
    textwriter.cpp
    valuemetric.cpp
    valuemetricvalues.cpp
    xmlwriter.cpp
    INSTALL lib64
    DEPENDS
)
vespa_generate_config(metrics metricsmanager.def)
install(FILES metricsmanager.def DESTINATION var/db/vespa/config_server/serverdb/classes)
